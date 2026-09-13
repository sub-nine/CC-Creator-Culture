package com.sub9.productservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * prod 경로(Flyway migrate 후 ddl-auto=validate)를 실제 PostgreSQL에서 재현한다.
 * db/migration의 SQL이 JPA 엔티티와 어긋나면 컨텍스트 기동 단계에서 실패한다.
 * 다른 통합 테스트가 create-drop으로 공유하는 컨테이너와 충돌하지 않도록 전용 컨테이너를 사용한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("상품 서비스 Flyway 마이그레이션 검증")
class FlywayMigrationValidationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17")
          .withDatabaseName("product_service_flyway_test")
          .withUsername("test")
          .withPassword("test");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @Autowired JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Test
  @DisplayName("Flyway 마이그레이션이 적용되면 이력에 버전 1이 성공으로 기록된다")
  void when_flyway_migration_is_applied_schema_history_has_version_1() {
    List<String> versions =
        jdbcTemplate.queryForList(
            """
            select version
              from public.flyway_schema_history
             where success
             order by installed_rank
            """,
            String.class);

    assertThat(versions).containsExactly("1");
  }

  @Test
  @DisplayName("Flyway 마이그레이션이 적용되면 엔티티 테이블이 모두 public 스키마에 생성된다")
  void when_flyway_migration_is_applied_entity_tables_exist_in_public_schema() {
    List<String> tables =
        jdbcTemplate.queryForList(
            """
            select table_name
              from information_schema.tables
             where table_schema = 'public'
               and table_type = 'BASE TABLE'
               and table_name <> 'flyway_schema_history'
             order by table_name
            """,
            String.class);

    assertThat(tables)
        .containsExactly(
            "p_categories",
            "p_categories_hashtags",
            "p_category_outbox_events",
            "p_hashtags",
            "p_hashtags_products",
            "p_images",
            "p_leaderboard_snapshots",
            "p_product_daily_views",
            "p_products",
            "p_skus",
            "p_stock_history",
            "p_stocks");
  }
}
