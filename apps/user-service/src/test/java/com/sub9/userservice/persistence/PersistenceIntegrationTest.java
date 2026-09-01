package com.sub9.userservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=private",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'"
})
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("사용자 도메인 PostgreSQL 영속성")
class PersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("user_service_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorRepository creatorRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("사용자와 연결된 PENDING 창작자를 저장한다")
    void when_user_and_creator_are_saved_relationship_and_initial_state_are_preserved() {
        User user = createUser("creator@example.com", "creator", "010-1111-2222", UserRole.CREATOR);
        userRepository.save(user);
        Creator creator = Creator.createPending(uuidGenerator.generate(), user.getUserId(), "창작상점",
                "123-45-67890", Instant.parse("2026-09-01T02:00:00Z"));

        creatorRepository.save(creator);
        entityManager.flush();
        entityManager.clear();

        Creator savedCreator = creatorRepository.findActiveByUserId(user.getUserId()).orElseThrow();
        assertThat(savedCreator.getApprovalStatus().name()).isEqualTo("PENDING");
        assertThat(savedCreator.getCreatedBy()).isNull();
        assertThat(savedCreator.getUpdatedBy()).isEqualTo(user.getUserId());
    }

    @Test
    @DisplayName("삭제된 사용자의 이메일도 UNIQUE 제약으로 재사용할 수 없다")
    void when_duplicate_email_is_saved_database_unique_constraint_rejects_it() {
        User first = createUser("same@example.com", "first", "010-1111-2222", UserRole.CUSTOMER);
        first.softDelete(first.getUserId(), Instant.parse("2026-09-01T03:00:00Z"));
        userRepository.save(first);
        entityManager.flush();

        User duplicate = createUser("same@example.com", "second", "010-3333-4444", UserRole.CUSTOMER);
        userRepository.save(duplicate);

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로 창작자를 저장할 수 없다")
    void when_creator_references_missing_user_database_foreign_key_rejects_it() {
        UUID missingUserId = uuidGenerator.generate();
        Creator creator = Creator.createPending(uuidGenerator.generate(), missingUserId, "창작상점",
                "123-45-67890", Instant.parse("2026-09-01T02:00:00Z"));
        creatorRepository.save(creator);

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("DB 연결 세션과 Hibernate가 UTC를 사용한다")
    void when_database_is_connected_session_time_zone_is_utc() {
        String timeZone = jdbcTemplate.queryForObject("show timezone", String.class);

        assertThat(timeZone).isEqualTo("UTC");
    }

    @Test
    @DisplayName("사용자와 창작자의 물리적 FK가 생성된다")
    void when_schema_is_created_expected_foreign_keys_exist() {
        Integer foreignKeyCount = jdbcTemplate.queryForObject("""
                select count(*)
                  from information_schema.table_constraints
                 where constraint_schema = 'private'
                   and constraint_type = 'FOREIGN KEY'
                   and constraint_name in (
                       'fk_users_created_by',
                       'fk_users_updated_by',
                       'fk_users_deleted_by',
                       'fk_creators_user',
                       'fk_creators_approved_by',
                       'fk_creators_created_by',
                       'fk_creators_updated_by',
                       'fk_creators_deleted_by'
                   )
                """, Integer.class);

        assertThat(foreignKeyCount).isEqualTo(8);
    }

    private User createUser(String email, String nickname, String phone, UserRole role) {
        UUID userId = uuidGenerator.generate();
        return User.create(userId, email, "encoded-password", nickname, phone,
                "서울시 예시구", null, role, Instant.parse("2026-09-01T01:30:00Z"));
    }
}
