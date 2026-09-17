package com.sub9.userservice.follow.application.service;

import static com.sub9.userservice.support.PostgresConcurrencySupport.await;
import static com.sub9.userservice.support.PostgresConcurrencySupport.awaitRowLock;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.follow.domain.exception.FollowErrorCode;
import com.sub9.userservice.follow.presentation.response.FollowStatusResponse;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import com.sub9.userservice.user.infrastructure.persistence.UserRepositoryImpl;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("팔로우 PostgreSQL 동시성")
class FollowConcurrencyIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");
    private static final Instant FOLLOWED_AT = Instant.parse("2026-09-16T02:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("user_follow_concurrency_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Autowired
    private FollowService followService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorRepository creatorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private UserRepositoryImpl lockedUsers;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from public.p_follows");
        jdbcTemplate.update("delete from public.p_creators");
        jdbcTemplate.update("delete from public.p_users");
    }

    @Test
    @DisplayName("동일 사용자가 같은 창작자를 동시에 팔로우하면 관계를 한 건만 생성한다")
    void when_same_user_follows_same_creator_concurrently_only_one_follow_is_created()
            throws Exception {
        // given: 작업 스레드가 조회할 수 있도록 테스트 데이터를 별도 트랜잭션에서 먼저 커밋한다.
        when(clock.instant()).thenReturn(FOLLOWED_AT);
        TestData data = saveApprovedCreatorAndCustomer();

        // when: 첫 번째 요청이 사용자 잠금을 선점한 상태에서 동일 팔로우 요청을 실행한다.
        RaceResult race = runConcurrentFollows(data.customerId(), data.creatorId());

        // then: 선점 요청만 성공하고 후행 요청은 중복 팔로우 오류로 종료된다.
        assertThat(race.winner().failure()).isNull();
        assertThat(race.winner().value())
                .extracting(FollowStatusResponse::creatorId, FollowStatusResponse::following)
                .containsExactly(data.creatorId(), true);
        assertThat(race.contender().value()).isNull();
        assertThat(race.contender().failure())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(FollowErrorCode.FOLLOW_ALREADY_EXISTS));

        // then: 경쟁이 끝난 뒤에도 사용자와 창작자 사이에는 하나의 활성 관계만 존재한다.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.p_follows", Integer.class)).isEqualTo(1);
        Map<String, Object> savedFollow = jdbcTemplate.queryForMap("""
                select user_id, creator_id, created_by, updated_by, deleted_at, deleted_by
                  from public.p_follows
                """);
        assertThat(savedFollow.get("user_id")).isEqualTo(data.customerId());
        assertThat(savedFollow.get("creator_id")).isEqualTo(data.creatorId());
        assertThat(savedFollow.get("created_by")).isEqualTo(data.customerId());
        assertThat(savedFollow.get("updated_by")).isEqualTo(data.customerId());
        assertThat(savedFollow.get("deleted_at")).isNull();
        assertThat(savedFollow.get("deleted_by")).isNull();
    }

    private RaceResult runConcurrentFollows(UUID customerId, UUID creatorId) throws Exception {
        CountDownLatch winnerLocked = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicBoolean firstRequest = new AtomicBoolean(true);

        // 실제 사용자 행 잠금을 획득한 첫 번째 요청만 정지시켜 잠금 경쟁을 재현한다.
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (firstRequest.getAndSet(false)) {
                winnerLocked.countDown();
                await(releaseWinner);
            }
            return result;
        }).when(lockedUsers).findActiveByIdForUpdate(customerId);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                // 첫 번째 요청이 사용자 행 잠금을 획득할 때까지 기다린다.
                Future<Attempt> winnerFuture = executor.submit(
                        () -> attempt(() -> followService.follow(customerId, creatorId)));
                assertThat(winnerLocked.await(5, SECONDS)).isTrue();

                // 동일한 팔로우 요청을 보내 첫 번째 트랜잭션의 잠금과 경쟁시킨다.
                Future<Attempt> contenderFuture = executor.submit(
                        () -> attempt(() -> followService.follow(customerId, creatorId)));

                // 단순 스레드 지연이 아니라 실제 PostgreSQL 잠금 대기인지 확인한다.
                awaitRowLock(jdbcTemplate, contenderFuture, "p_users");

                // 첫 번째 요청을 커밋시켜 후행 요청이 변경된 상태를 다시 확인하게 한다.
                releaseWinner.countDown();
                return new RaceResult(
                        winnerFuture.get(10, SECONDS),
                        contenderFuture.get(10, SECONDS));
            } finally {
                // assertion 실패 시에도 대기 중인 스레드가 남지 않도록 잠금을 해제한다.
                releaseWinner.countDown();
            }
        }
    }

    private TestData saveApprovedCreatorAndCustomer() {
        // 작업 스레드가 미커밋 데이터를 기다리지 않도록 준비 데이터를 먼저 커밋한다.
        return transaction().execute(status -> {
            User customer = createUser(
                    "follow-race-customer@example.com",
                    "follow-race-customer",
                    "010-1000-0001",
                    UserRole.CUSTOMER);
            User creatorUser = createUser(
                    "follow-race-creator@example.com",
                    "follow-race-creator",
                    "010-1000-0002",
                    UserRole.CREATOR);
            User manager = createUser(
                    "follow-race-manager@example.com",
                    "follow-race-manager",
                    "010-1000-0003",
                    UserRole.MANAGER);
            userRepository.save(customer);
            userRepository.save(creatorUser);
            userRepository.save(manager);

            Creator creator = Creator.createPending(
                    uuidGenerator.generate(),
                    creatorUser.getId(),
                    "동시팔로우상점",
                    "100-00-00001",
                    CREATED_AT);
            creator.approve(manager.getId(), CREATED_AT.plusSeconds(60));
            creatorRepository.save(creator);

            return new TestData(customer.getId(), creator.getId());
        });
    }

    private User createUser(String email, String nickname, String phone, UserRole role) {
        return User.create(
                uuidGenerator.generate(),
                email,
                "encoded-password",
                nickname,
                phone,
                "서울시 예시구",
                null,
                role,
                CREATED_AT);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private Attempt attempt(Supplier<FollowStatusResponse> action) {
        try {
            return new Attempt(action.get(), null);
        } catch (RuntimeException exception) {
            return new Attempt(null, exception);
        }
    }

    private record TestData(UUID customerId, UUID creatorId) {
    }

    private record Attempt(FollowStatusResponse value, RuntimeException failure) {
    }

    private record RaceResult(Attempt winner, Attempt contender) {
    }
}
