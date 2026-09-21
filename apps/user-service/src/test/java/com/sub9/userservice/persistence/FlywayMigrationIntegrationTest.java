package com.sub9.userservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DisplayName("User 서비스 Flyway 배포 이력 호환성")
class FlywayMigrationIntegrationTest {

    @Container
    final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private Flyway migrations(String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("public")
                .defaultSchema("public")
                .target(target)
                .load();
    }

    @Test
    @DisplayName("V4까지 적용된 DB일 때 업그레이드하면 기존 이력과 데이터를 보존한다")
    void when_v4_is_applied_upgrade_preserves_history_and_data() throws Exception {
        migrations("4").migrate();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            // 실제 개발 DB의 적용 체크섬을 고정해 이미 배포된 SQL 변경을 감지한다.
            try (var rows = statement.executeQuery("""
                    SELECT checksum FROM public.flyway_schema_history
                    WHERE version IN ('2', '4') ORDER BY version
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(-2113436924);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(448675675);
            }
            statement.executeUpdate("""
                    INSERT INTO public.p_users
                        (id, email, password, nickname, phone, address, role, updated_by)
                    VALUES ('00000000-0000-0000-0000-000000000001', 'migration@example.test',
                        'unused', 'migration', '010-0000-0000', 'test', 'USER',
                        '00000000-0000-0000-0000-000000000001')
                    """);
            assertThat(migrations("latest").migrate().migrationsExecuted).isEqualTo(1);
            migrations("latest").validate();
            try (var rows = statement.executeQuery("""
                    SELECT count(*), to_regnamespace('private') FROM public.p_users
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getString(2)).isNull();
            }
        }
    }

    @Test
    @DisplayName("private에 미확인 테이블이 있을 때 업그레이드하면 삭제 없이 실패한다")
    void when_private_has_unknown_table_upgrade_fails_without_deleting_it() throws Exception {
        migrations("4").migrate();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE private.must_keep (id integer)");
            statement.execute("INSERT INTO private.must_keep VALUES (1)");
            assertThatThrownBy(() -> migrations("latest").migrate()).isInstanceOf(FlywayException.class);
            try (var rows = statement.executeQuery("SELECT id FROM private.must_keep")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }
}
