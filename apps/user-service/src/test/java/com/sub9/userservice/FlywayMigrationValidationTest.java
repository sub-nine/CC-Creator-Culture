package com.sub9.userservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * prod 경로(Flyway migrate 후 ddl-auto=validate)를 실제 PostgreSQL에서 재현한다.
 * db/migration의 SQL이 JPA 엔티티와 어긋나면 컨텍스트 기동 단계에서 실패한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=private",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=private",
        "spring.flyway.default-schema=private",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
        "spring.kafka.listener.auto-startup=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("사용자 서비스 Flyway 마이그레이션 검증")
class FlywayMigrationValidationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("user_service_flyway_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("Flyway 마이그레이션이 적용되면 private 스키마의 이력에 버전 1이 성공으로 기록된다")
    void when_flyway_migration_is_applied_schema_history_in_private_schema_has_version_1() {
        // Flyway가 private 스키마를 직접 만들면 version이 null인 "Schema Creation" 행이 먼저 기록되므로 제외한다.
        List<String> versions = jdbcTemplate.queryForList("""
                select version
                  from private.flyway_schema_history
                 where success
                   and version is not null
                 order by installed_rank
                """, String.class);

        assertThat(versions).containsExactly("1");
    }

    @Test
    @DisplayName("Flyway 마이그레이션이 적용되면 엔티티 테이블이 모두 private 스키마에 생성된다")
    void when_flyway_migration_is_applied_entity_tables_exist_in_private_schema() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name
                  from information_schema.tables
                 where table_schema = 'private'
                   and table_type = 'BASE TABLE'
                   and table_name <> 'flyway_schema_history'
                 order by table_name
                """, String.class);

        assertThat(tables).containsExactly(
                "notification",
                "notification_event",
                "p_creators",
                "p_notification_slack",
                "p_users");
    }
}
