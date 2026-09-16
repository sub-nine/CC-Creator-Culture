package com.sub9.userservice.creator;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("창작자 상호명 PostgreSQL 조회")
class CreatorNameQueryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-15T01:00:00Z");
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("creator_name_query_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired private UserRepository userRepository;
    @Autowired private CreatorRepository creatorRepository;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("승인되고 사용자와 창작자가 모두 미삭제인 창작자만 조회한다")
    void when_mixed_creator_states_are_requested_only_approved_active_creator_is_returned() {
        User manager = saveUser("manager", UserRole.MANAGER);
        User approvedUser = saveUser("approved", UserRole.CREATOR);
        Creator approved = saveCreator(approvedUser, "승인상점");
        approved.approve(manager.getId(), NOW.plusSeconds(1));

        User pendingUser = saveUser("pending", UserRole.CREATOR);
        saveCreator(pendingUser, "대기상점");

        User rejectedUser = saveUser("rejected", UserRole.CREATOR);
        Creator rejected = saveCreator(rejectedUser, "거절상점");
        rejected.reject(manager.getId(), NOW.plusSeconds(2));

        User deletedCreatorUser = saveUser("deleted-creator", UserRole.CREATOR);
        Creator deletedCreator = saveCreator(deletedCreatorUser, "삭제상점");
        deletedCreator.approve(manager.getId(), NOW.plusSeconds(3));
        deletedCreator.softDelete(manager.getId(), NOW.plusSeconds(4));

        User deletedUser = saveUser("deleted-user", UserRole.CREATOR);
        Creator creatorOfDeletedUser = saveCreator(deletedUser, "탈퇴상점");
        creatorOfDeletedUser.approve(manager.getId(), NOW.plusSeconds(5));
        deletedUser.softDelete(deletedUser.getId(), NOW.plusSeconds(6));

        entityManager.flush();
        entityManager.clear();

        List<Creator> result = creatorRepository.findApprovedActiveByUserIds(List.of(
                approvedUser.getId(), pendingUser.getId(), rejectedUser.getId(),
                deletedCreatorUser.getId(), deletedUser.getId(), uuidGenerator.generate()));

        assertThat(result).singleElement().satisfies(creator -> {
            assertThat(creator.getUserId()).isEqualTo(approvedUser.getId());
            assertThat(creator.getCreatorName()).isEqualTo("승인상점");
        });
    }

    private User saveUser(String name, UserRole role) {
        User user = User.create(
                uuidGenerator.generate(), name + "@example.com", "encoded-password", name,
                "010" + Math.abs(name.hashCode()), "서울시 예시구", null, role, NOW);
        return userRepository.save(user);
    }

    private Creator saveCreator(User user, String creatorName) {
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), user.getId(), creatorName,
                UUID.randomUUID().toString().replace("-", "").substring(0, 10), NOW);
        return creatorRepository.save(creator);
    }
}
