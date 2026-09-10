package com.sub9.userservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.follow.domain.model.Follow;
import com.sub9.userservice.follow.domain.repository.FollowRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.LocalDateTime;
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
    private FollowRepository followRepository;

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
        Creator creator = Creator.createPending(uuidGenerator.generate(), user.getId(), "창작상점",
                "123-45-67890", Instant.parse("2026-09-01T02:00:00Z"));

        creatorRepository.save(creator);
        entityManager.flush();
        entityManager.clear();

        Creator savedCreator = creatorRepository.findActiveByUserId(user.getId()).orElseThrow();
        assertThat(savedCreator.getApprovalStatus().name()).isEqualTo("PENDING");
        assertThat(savedCreator.getCreatedBy()).isNull();
        assertThat(savedCreator.getUpdatedBy()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("잠금 조회한 PENDING 창작자의 승인 및 감사 정보를 저장한다")
    void when_locked_pending_creator_is_approved_approval_and_audit_are_persisted() {
        User creatorUser = createUser(
                "approval-creator@example.com",
                "approval-creator",
                "010-1111-3333",
                UserRole.CREATOR);
        User manager = createUser(
                "approval-manager@example.com",
                "approval-manager",
                "010-1111-4444",
                UserRole.MANAGER);
        userRepository.save(creatorUser);
        userRepository.save(manager);
        Creator creator = Creator.createPending(
                uuidGenerator.generate(),
                creatorUser.getId(),
                "승인대상상점",
                "987-65-43210",
                Instant.parse("2026-09-01T02:00:00Z"));
        creatorRepository.save(creator);
        entityManager.flush();
        entityManager.clear();

        Instant approvedAt = Instant.parse("2026-09-10T02:00:00Z");
        Creator lockedCreator = creatorRepository.findActiveByIdForUpdate(creator.getId())
                .orElseThrow();
        lockedCreator.approve(manager.getId(), approvedAt);
        entityManager.flush();
        entityManager.clear();

        Creator savedCreator = creatorRepository.findActiveById(creator.getId()).orElseThrow();
        assertThat(savedCreator.getApprovalStatus().name()).isEqualTo("APPROVED");
        assertThat(savedCreator.getApprovedBy()).isEqualTo(manager.getId());
        assertThat(savedCreator.getApprovedAt())
                .isEqualTo(LocalDateTime.parse("2026-09-10T02:00:00"));
        assertThat(savedCreator.getCreatedBy()).isEqualTo(manager.getId());
        assertThat(savedCreator.getUpdatedBy()).isEqualTo(manager.getId());
        assertThat(savedCreator.getUpdatedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("팔로우를 저장하고 삭제 관계를 잠금 조회하여 복구한다")
    void when_deleted_follow_is_locked_and_restored_original_creation_audit_is_preserved() {
        User customer = createUser(
                "follow-customer@example.com", "follow-customer", "010-2111-2222",
                UserRole.CUSTOMER);
        User creatorUser = createUser(
                "follow-creator@example.com", "follow-creator", "010-2333-4444",
                UserRole.CREATOR);
        userRepository.save(customer);
        userRepository.save(creatorUser);
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), creatorUser.getId(), "팔로우상점", "111-22-33333",
                Instant.parse("2026-09-10T02:00:00Z"));
        creatorRepository.save(creator);
        Follow follow = Follow.create(
                uuidGenerator.generate(), customer.getId(), creator.getId(),
                Instant.parse("2026-09-10T04:00:00Z"));
        follow.unfollow(customer.getId(), Instant.parse("2026-09-10T05:00:00Z"));
        followRepository.save(follow);
        entityManager.flush();
        entityManager.clear();

        Follow deletedFollow = followRepository.findByUserIdAndCreatorIdForUpdate(
                customer.getId(), creator.getId()).orElseThrow();
        deletedFollow.restore(customer.getId(), Instant.parse("2026-09-10T06:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        Follow restoredFollow = followRepository.findByUserIdAndCreatorIdForUpdate(
                customer.getId(), creator.getId()).orElseThrow();
        assertThat(restoredFollow.getId()).isEqualTo(follow.getId());
        assertThat(restoredFollow.getCreatedAt())
                .isEqualTo(Instant.parse("2026-09-10T04:00:00Z"));
        assertThat(restoredFollow.getCreatedBy()).isEqualTo(customer.getId());
        assertThat(restoredFollow.getUpdatedAt())
                .isEqualTo(Instant.parse("2026-09-10T06:00:00Z"));
        assertThat(restoredFollow.getDeletedAt()).isNull();
        assertThat(restoredFollow.getDeletedBy()).isNull();
    }

    @Test
    @DisplayName("동일 CUSTOMER와 Creator의 팔로우 관계를 중복 저장할 수 없다")
    void when_duplicate_follow_is_saved_unique_constraint_rejects_it() {
        User customer = createUser(
                "unique-customer@example.com", "unique-customer", "010-3111-2222",
                UserRole.CUSTOMER);
        User creatorUser = createUser(
                "unique-creator@example.com", "unique-creator", "010-3333-4444",
                UserRole.CREATOR);
        userRepository.save(customer);
        userRepository.save(creatorUser);
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), creatorUser.getId(), "중복확인상점", "222-33-44444",
                Instant.parse("2026-09-10T02:00:00Z"));
        creatorRepository.save(creator);
        followRepository.save(Follow.create(
                uuidGenerator.generate(), customer.getId(), creator.getId(),
                Instant.parse("2026-09-10T04:00:00Z")));
        entityManager.flush();

        followRepository.save(Follow.create(
                uuidGenerator.generate(), customer.getId(), creator.getId(),
                Instant.parse("2026-09-10T05:00:00Z")));

        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("승인되고 삭제되지 않은 창작자만 팔로우 대상으로 조회한다")
    void when_creator_is_approved_and_active_approved_lookup_returns_creator() {
        User creatorUser = createUser(
                "approved-follow@example.com", "approved-follow", "010-4111-2222",
                UserRole.CREATOR);
        User manager = createUser(
                "follow-manager@example.com", "follow-manager", "010-4333-4444",
                UserRole.MANAGER);
        userRepository.save(creatorUser);
        userRepository.save(manager);
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), creatorUser.getId(), "승인팔로우상점", "333-44-55555",
                Instant.parse("2026-09-10T02:00:00Z"));
        creator.approve(manager.getId(), Instant.parse("2026-09-10T03:00:00Z"));
        creatorRepository.save(creator);
        entityManager.flush();
        entityManager.clear();

        assertThat(creatorRepository.findApprovedActiveById(creator.getId())).isPresent();
    }

    @Test
    @DisplayName("팔로우 테이블의 사용자 및 창작자 외래 키를 생성한다")
    void when_follow_schema_is_created_expected_foreign_keys_exist() {
        Integer foreignKeyCount = jdbcTemplate.queryForObject("""
                select count(*)
                  from information_schema.table_constraints
                 where constraint_schema = 'private'
                   and table_name = 'p_follows'
                   and constraint_type = 'FOREIGN KEY'
                   and constraint_name in (
                       'fk_follows_user',
                       'fk_follows_creator',
                       'fk_follows_created_by',
                       'fk_follows_updated_by',
                       'fk_follows_deleted_by'
                   )
                """, Integer.class);

        assertThat(foreignKeyCount).isEqualTo(5);
    }

    @Test
    @DisplayName("삭제된 사용자의 이메일도 UNIQUE 제약으로 재사용할 수 없다")
    void when_duplicate_email_is_saved_database_unique_constraint_rejects_it() {
        User first = createUser("same@example.com", "first", "010-1111-2222", UserRole.CUSTOMER);
        first.softDelete(first.getId(), Instant.parse("2026-09-01T03:00:00Z"));
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

    @Test
    @DisplayName("사용자와 창작자의 감사 시각 컬럼을 timestamp with time zone으로 생성한다")
    void when_schema_is_created_audit_timestamp_columns_include_time_zone() {
        Integer timestampWithTimeZoneCount = jdbcTemplate.queryForObject("""
                select count(*)
                  from information_schema.columns
                 where table_schema = 'private'
                   and table_name in ('p_users', 'p_creators')
                   and column_name in ('created_at', 'updated_at', 'deleted_at')
                   and data_type = 'timestamp with time zone'
                """, Integer.class);

        assertThat(timestampWithTimeZoneCount).isEqualTo(6);
    }

    private User createUser(String email, String nickname, String phone, UserRole role) {
        UUID userId = uuidGenerator.generate();
        return User.create(userId, email, "encoded-password", nickname, phone,
                "서울시 예시구", null, role, Instant.parse("2026-09-01T01:30:00Z"));
    }
}
