package com.sub9.userservice.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.common.kafka.event.UserDeletedEvent;
import com.sub9.common.kafka.topic.KafkaTopics;
import com.sub9.userservice.auth.application.service.LogoutService;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.infrastructure.persistence.CreatorJpaRepository;
import com.sub9.userservice.follow.domain.model.Follow;
import com.sub9.userservice.follow.domain.repository.FollowRepository;
import com.sub9.userservice.follow.infrastructure.persistence.FollowJpaRepository;
import com.sub9.userservice.user.application.service.UserDeletionService;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import com.sub9.userservice.user.infrastructure.persistence.UserJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("회원 탈퇴 PostgreSQL 통합")
class UserDeletionIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-14T07:00:00Z");
    private static final long EXPIRES_AT = CREATED_AT.plusSeconds(3600).getEpochSecond();

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("user_deletion_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired private UserDeletionService userDeletionService;
    @Autowired private UserRepository userRepository;
    @Autowired private CreatorRepository creatorRepository;
    @Autowired private FollowRepository followRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private CreatorJpaRepository creatorJpaRepository;
    @Autowired private FollowJpaRepository followJpaRepository;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean
    private LogoutService logoutService;
    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUpKafka() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("CUSTOMER 탈퇴는 본인의 활성 Follow만 같은 감사 정보로 삭제한다")
    void when_customer_deletes_account_only_active_owned_follows_are_soft_deleted() {
        User customer = saveUser("customer-delete", UserRole.CUSTOMER, "01010000001");
        User otherCustomer = saveUser("other-customer", UserRole.CUSTOMER, "01010000002");
        Creator firstCreator = saveCreator("first-creator", "1111111111");
        Creator secondCreator = saveCreator("second-creator", "2222222222");
        Follow activeFollow = saveFollow(customer.getId(), firstCreator.getId());
        Follow alreadyDeletedFollow = saveFollow(customer.getId(), secondCreator.getId());
        Instant previousDeletedAt = CREATED_AT.plusSeconds(30);
        alreadyDeletedFollow.unfollow(customer.getId(), previousDeletedAt);
        followRepository.save(alreadyDeletedFollow);
        followRepository.flush();
        Follow unrelatedFollow = saveFollow(otherCustomer.getId(), firstCreator.getId());

        userDeletionService.deleteMyAccount(
                customer.getId(), uuidGenerator.generate(), EXPIRES_AT);

        User deletedUser = userJpaRepository.findById(customer.getId()).orElseThrow();
        Follow deletedFollow = followJpaRepository.findById(activeFollow.getId()).orElseThrow();
        Follow preservedFollow = followJpaRepository.findById(alreadyDeletedFollow.getId())
                .orElseThrow();
        Follow remainingFollow = followJpaRepository.findById(unrelatedFollow.getId()).orElseThrow();
        assertThat(deletedUser.isDeleted()).isTrue();
        assertThat(deletedFollow.isDeleted()).isTrue();
        assertThat(deletedFollow.getDeletedAt()).isEqualTo(deletedUser.getDeletedAt());
        assertThat(deletedFollow.getDeletedBy()).isEqualTo(customer.getId());
        assertThat(preservedFollow.getDeletedAt()).isEqualTo(previousDeletedAt);
        assertThat(remainingFollow.isDeleted()).isFalse();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("CREATOR 탈퇴는 Creator와 해당 Creator 대상 활성 Follow를 삭제한다")
    void when_creator_deletes_account_creator_and_target_follows_are_soft_deleted() {
        User creatorUser = saveUser("creator-delete", UserRole.CREATOR, "01020000001");
        Creator creator = saveCreator(creatorUser, "delete-target", "3333333333");
        User follower = saveUser("creator-follower", UserRole.CUSTOMER, "01020000002");
        Follow targetFollow = saveFollow(follower.getId(), creator.getId());
        Creator unrelatedCreator = saveCreator("unrelated-creator", "4444444444");
        Follow unrelatedFollow = saveFollow(follower.getId(), unrelatedCreator.getId());

        userDeletionService.deleteMyAccount(
                creatorUser.getId(), uuidGenerator.generate(), EXPIRES_AT);

        User deletedUser = userJpaRepository.findById(creatorUser.getId()).orElseThrow();
        Creator deletedCreator = creatorJpaRepository.findById(creator.getId()).orElseThrow();
        Follow deletedFollow = followJpaRepository.findById(targetFollow.getId()).orElseThrow();
        Follow remainingFollow = followJpaRepository.findById(unrelatedFollow.getId()).orElseThrow();
        assertThat(deletedUser.isDeleted()).isTrue();
        assertThat(deletedCreator.isDeleted()).isTrue();
        assertThat(deletedFollow.isDeleted()).isTrue();
        assertThat(deletedCreator.getDeletedAt()).isEqualTo(deletedUser.getDeletedAt());
        assertThat(deletedFollow.getDeletedAt()).isEqualTo(deletedUser.getDeletedAt());
        assertThat(deletedCreator.getDeletedBy()).isEqualTo(creatorUser.getId());
        assertThat(deletedFollow.getDeletedBy()).isEqualTo(creatorUser.getId());
        assertThat(remainingFollow.isDeleted()).isFalse();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(
                eq(KafkaTopics.USER_DELETED),
                eq(creatorUser.getId().toString()),
                payloadCaptor.capture());
        UserDeletedEvent event =
                jsonMapper.readValue(payloadCaptor.getValue(), UserDeletedEvent.class);
        assertThat(event.userId()).isEqualTo(creatorUser.getId());
        assertThat(event.occurredAt())
                .isCloseTo(deletedUser.getDeletedAt(), within(1, ChronoUnit.MICROS));
        assertThat(event.eventId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("CREATOR 탈퇴 트랜잭션이 롤백되면 이벤트를 발행하지 않는다")
    void when_creator_deletion_rolls_back_event_is_not_published() {
        User creatorUser = saveUser("rollback-creator", UserRole.CREATOR, "01030000001");
        Creator creator = saveCreator(creatorUser, "rollback-target", "5555555555");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            userDeletionService.deleteMyAccount(
                    creatorUser.getId(), uuidGenerator.generate(), EXPIRES_AT);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        assertThat(userRepository.findActiveById(creatorUser.getId())).isPresent();
        Creator activeCreator = creatorRepository.findActiveByUserId(creatorUser.getId())
                .orElseThrow();
        assertThat(activeCreator.getId()).isEqualTo(creator.getId());
        assertThat(activeCreator.isDeleted()).isFalse();
    }

    private User saveUser(String name, UserRole role, String phone) {
        User user = User.create(
                uuidGenerator.generate(), name + "@example.com", "encoded-password", name,
                phone, "서울시 예시구", null, role, CREATED_AT);
        userRepository.save(user);
        userRepository.flush();
        return user;
    }

    private Creator saveCreator(String name, String businessNumber) {
        User user = saveUser(name + "-user", UserRole.CREATOR, nextPhone());
        return saveCreator(user, name, businessNumber);
    }

    private Creator saveCreator(User user, String name, String businessNumber) {
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), user.getId(), name, businessNumber, CREATED_AT);
        creatorRepository.save(creator);
        creatorRepository.flush();
        return creator;
    }

    private Follow saveFollow(UUID userId, UUID creatorId) {
        Follow follow = Follow.create(uuidGenerator.generate(), userId, creatorId, CREATED_AT);
        followRepository.save(follow);
        followRepository.flush();
        return follow;
    }

    private String nextPhone() {
        String digits = uuidGenerator.generate().toString().replace("-", "");
        return digits.substring(0, 20);
    }
}
