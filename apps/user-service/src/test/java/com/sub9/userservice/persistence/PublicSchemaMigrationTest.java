package com.sub9.userservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DisplayName("User public 스키마 마이그레이션")
class PublicSchemaMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA IF EXISTS private CASCADE");
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
    }

    @Test
    @DisplayName("빈 DB일 때 마이그레이션하면 public에 전체 테이블이 생성된다")
    void when_database_is_empty_migrating_creates_public_tables() {
        flyway("public", null).migrate();
        assertThat(tables("public")).containsExactlyInAnyOrder("flyway_schema_history", "p_users",
                "p_creators", "p_follows", "notification", "notification_event", "p_notification_slack");
        assertThat(tables("private")).isEmpty();
        assertThat(flyway("public", null).migrate().migrationsExecuted).isZero();
        flyway("public", null).validate();
    }

    @Test
    @DisplayName("V1 데이터가 있을 때 이전하면 데이터와 객체 식별자 및 권한이 보존된다")
    void when_v1_has_data_migrating_preserves_data_objects_and_privileges() throws Exception {
        seedV1();
        // Hibernate가 이미 생성한 팔로우 테이블도 데이터와 함께 이동해야 한다.
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V3__create_missing_follows.sql"))
                .replace("public.", "private."));
        jdbc.execute("""
                INSERT INTO private.p_creators (id, user_id, creator_name, business_registration_number,
                    approval_status, updated_by) VALUES
                    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
                    'creator', 'registration', 'APPROVED', '00000000-0000-0000-0000-000000000001');
                INSERT INTO private.p_follows (id, user_id, creator_id, created_by, updated_by) VALUES
                    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001',
                    '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
                    '00000000-0000-0000-0000-000000000001');
                GRANT SELECT ON private.p_users TO PUBLIC;
                """);
        List<Map<String, Object>> objects = objects("private");
        List<Map<String, Object>> constraints = constraints();
        List<Map<String, Object>> data = data("private");
        List<Map<String, Object>> history = jdbc.queryForList("SELECT * FROM private.flyway_schema_history ORDER BY installed_rank");

        executeOperationalSql("prepare-user-public-schema.sql");
        flyway("public", null).migrate();

        assertThat(objects("public")).containsAll(objects);
        assertThat(constraints()).containsAll(constraints);
        assertThat(data("public")).isEqualTo(data);
        assertThat(jdbc.queryForList("SELECT * FROM public.flyway_schema_history WHERE version IS NULL OR version = '1' ORDER BY installed_rank"))
                .isEqualTo(history);
        assertThat(flyway("public", null).migrate().migrationsExecuted).isZero();
    }

    @Test
    @DisplayName("이력 이전을 생략했을 때 새 설정으로 기동하면 기존 데이터가 보존된 채 실패한다")
    void when_history_is_not_moved_migrating_fails_without_changing_data() {
        seedV1();
        assertThatThrownBy(() -> flyway("public", null).migrate()).isInstanceOf(RuntimeException.class);
        assertThat(tables("private")).contains("p_users", "flyway_schema_history");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM private.p_users", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("대상 테이블이 있을 때 준비 SQL을 실행하면 이력을 이전하지 않는다")
    void when_target_conflicts_preparing_leaves_history_in_private() {
        seedV1();
        jdbc.execute("CREATE TABLE public.p_users (id int)");
        assertThatThrownBy(() -> executeOperationalSql("prepare-user-public-schema.sql"))
                .isInstanceOf(RuntimeException.class);
        assertThat(tables("private")).contains("flyway_schema_history", "p_users");
        assertThat(tables("public")).containsExactly("p_users");
    }

    @Test
    @DisplayName("이전 도중 인덱스 이름이 충돌하면 V2 전체가 롤백된다")
    void when_index_conflicts_migrating_rolls_back_all_table_moves() throws Exception {
        seedV1();
        executeOperationalSql("prepare-user-public-schema.sql");
        jdbc.execute("CREATE TABLE public.idx_notification_slack_ready (id int)");
        assertThatThrownBy(() -> flyway("public", null).migrate()).isInstanceOf(RuntimeException.class);
        assertThat(tables("private")).contains("p_users", "p_creators", "notification", "p_notification_slack");
        assertThat(tables("public")).doesNotContain("p_users", "p_creators");
        assertThat(jdbc.queryForList("SELECT version FROM public.flyway_schema_history WHERE version IS NOT NULL", String.class))
                .containsExactly("1");
    }

    @Test
    @DisplayName("예상하지 못한 테이블이 있을 때 준비 SQL을 실행하면 변경 없이 실패한다")
    void when_unknown_table_exists_preparing_fails_without_changes() {
        seedV1();
        jdbc.execute("CREATE TABLE private.unknown_table (id int)");
        assertThatThrownBy(() -> executeOperationalSql("prepare-user-public-schema.sql"))
                .isInstanceOf(RuntimeException.class);
        assertThat(tables("private")).contains("flyway_schema_history", "p_users", "unknown_table");
    }

    @Test
    @DisplayName("이전 완료 후 복구하면 데이터와 V1 이력이 private으로 돌아가고 재전환할 수 있다")
    void when_migration_completed_rolling_back_preserves_data_and_allows_retry() throws Exception {
        seedV1();
        executeOperationalSql("prepare-user-public-schema.sql");
        flyway("public", null).migrate();
        List<Map<String, Object>> data = data("public");
        jdbc.execute("DROP SCHEMA private");

        executeOperationalSql("rollback-user-public-schema.sql");

        assertThat(data("private")).isEqualTo(data);
        assertThat(tables("public")).isEmpty();
        assertThat(jdbc.queryForList("SELECT version FROM private.flyway_schema_history WHERE version IS NOT NULL", String.class))
                .containsExactly("1");
        flyway("private", "1").validate();
        executeOperationalSql("prepare-user-public-schema.sql");
        flyway("public", null).migrate();
        assertThat(data("public")).isEqualTo(data);
    }

    @Test
    @DisplayName("이력만 이전한 상태에서 복구하면 테이블을 유지하고 이력이 원복된다")
    void when_only_history_moved_rolling_back_restores_history() throws Exception {
        seedV1();
        executeOperationalSql("prepare-user-public-schema.sql");
        executeOperationalSql("rollback-user-public-schema.sql");
        assertThat(tables("private")).contains("flyway_schema_history", "p_users");
        assertThat(tables("public")).isEmpty();
        flyway("private", "1").validate();
    }

    private void seedV1() {
        flyway("private", "1").migrate();
        jdbc.execute("""
                INSERT INTO private.p_users (id, email, password, nickname, phone, address, role, updated_by)
                VALUES ('00000000-0000-0000-0000-000000000001', 'schema@example.test', 'hash', 'schema',
                    '01012345678', 'address', 'CUSTOMER', '00000000-0000-0000-0000-000000000001')
                """);
    }

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private List<String> tables(String schema) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = ?", String.class, schema);
    }

    private List<Map<String, Object>> objects(String schema) {
        return jdbc.queryForList("""
                SELECT c.oid, c.relname, c.relkind, c.relowner, c.relacl::text
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? ORDER BY c.oid
                """, schema);
    }

    private List<Map<String, Object>> constraints() {
        return jdbc.queryForList("""
                SELECT oid, conname, contype, conrelid, confrelid, conkey::text, confkey::text
                FROM pg_constraint WHERE conrelid IN (SELECT oid FROM pg_class
                    WHERE relname IN ('p_users', 'p_creators', 'p_follows')) ORDER BY oid
                """);
    }

    private List<Map<String, Object>> data(String schema) {
        return jdbc.queryForList("SELECT row_to_json(t)::text AS row FROM " + schema + ".p_users t UNION ALL "
                + "SELECT row_to_json(t)::text FROM " + schema + ".p_creators t UNION ALL "
                + "SELECT row_to_json(t)::text FROM " + schema + ".p_follows t ORDER BY row");
    }

    private void executeOperationalSql(String filename) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("deploy/postgres"))) root = root.getParent();
        jdbc.execute(Files.readString(root.resolve("deploy/postgres").resolve(filename)));
    }
}
