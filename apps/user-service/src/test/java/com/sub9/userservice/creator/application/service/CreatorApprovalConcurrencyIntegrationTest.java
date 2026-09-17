package com.sub9.userservice.creator.application.service;

import static com.sub9.userservice.support.PostgresConcurrencySupport.await;
import static com.sub9.userservice.support.PostgresConcurrencySupport.awaitRowLock;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.infrastructure.persistence.CreatorRepositoryImpl;
import com.sub9.userservice.creator.presentation.request.CreatorApprovalRequest;
import com.sub9.userservice.creator.presentation.response.CreatorApprovalResponse;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@DisplayName("창작자 승인 PostgreSQL 동시성")
class CreatorApprovalConcurrencyIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-09-16T02:00:00Z");
    private static final CreatorApprovalRequest APPROVE_REQUEST =
            new CreatorApprovalRequest(ApprovalStatus.APPROVED);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("user_creator_approval_concurrency_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Autowired
    private CreatorApprovalService creatorApprovalService;

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
    private CreatorRepositoryImpl lockedCreators;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from public.p_creators");
        jdbcTemplate.update("delete from public.p_users");
    }

    @ParameterizedTest(name = "{0}가 잠금을 선점하면 해당 관리자만 승인에 성공한다")
    @EnumSource(value = UserRole.class, names = {"MASTER", "MANAGER"})
    @DisplayName("MASTER와 MANAGER가 같은 창작자를 동시에 승인하면 한 요청만 성공한다")
    void when_master_and_manager_approve_same_creator_concurrently_only_lock_winner_approves(
            UserRole winnerRole) throws Exception {
        // given: 작업 스레드가 조회할 수 있도록 관리자와 창작자를 먼저 커밋한다.
        when(clock.instant()).thenReturn(REVIEWED_AT);
        TestData data = savePendingCreatorWithAdmins();
        UUID winnerId = data.adminId(winnerRole);
        UUID contenderId = data.otherAdminId(winnerRole);

        // when: 선택한 관리자가 창작자 잠금을 선점한 상태에서 다른 관리자의 승인을 실행한다.
        RaceResult race = runConcurrentApprovals(data.creatorId(), winnerId, contenderId);

        // then: 잠금을 선점한 관리자만 승인하고 후행 요청은 이미 심사된 오류로 종료된다.
        assertThat(race.winner().failure()).isNull();
        assertThat(race.winner().value())
                .extracting(
                        CreatorApprovalResponse::creatorId,
                        CreatorApprovalResponse::approvalStatus,
                        CreatorApprovalResponse::approvedBy,
                        CreatorApprovalResponse::approvedAt)
                .containsExactly(
                        data.creatorId(),
                        ApprovalStatus.APPROVED,
                        winnerId,
                        LocalDateTime.ofInstant(REVIEWED_AT, ZoneOffset.UTC));
        assertThat(race.contender().value()).isNull();
        assertThat(race.contender().failure())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(CreatorErrorCode.CREATOR_ALREADY_REVIEWED));

        // then: 최종 승인 상태와 감사 정보는 실제로 성공한 관리자 기준으로 유지된다.
        Creator savedCreator = findCreator(data.creatorId());
        assertThat(savedCreator.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(savedCreator.getApprovedBy()).isEqualTo(winnerId);
        assertThat(savedCreator.getApprovedAt())
                .isEqualTo(LocalDateTime.ofInstant(REVIEWED_AT, ZoneOffset.UTC));
        assertThat(savedCreator.getCreatedBy()).isEqualTo(winnerId);
        assertThat(savedCreator.getUpdatedBy()).isEqualTo(winnerId);
        assertThat(savedCreator.getUpdatedAt()).isEqualTo(REVIEWED_AT);
    }

    private RaceResult runConcurrentApprovals(
            UUID creatorId, UUID winnerId, UUID contenderId) throws Exception {
        CountDownLatch winnerLocked = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicBoolean firstRequest = new AtomicBoolean(true);

        // 실제 창작자 행 잠금을 획득한 첫 번째 요청만 정지시켜 잠금 경쟁을 재현한다.
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (firstRequest.getAndSet(false)) {
                winnerLocked.countDown();
                await(releaseWinner);
            }
            return result;
        }).when(lockedCreators).findActiveByIdForUpdate(creatorId);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                // 선행 요청이 창작자 행 잠금을 획득할 때까지 기다린다.
                Future<Attempt> winnerFuture = executor.submit(
                        () -> attempt(() -> creatorApprovalService.review(
                                creatorId, winnerId, APPROVE_REQUEST)));
                assertThat(winnerLocked.await(5, SECONDS)).isTrue();

                // 다른 권한의 관리자가 동일한 PENDING 창작자를 승인하도록 경쟁시킨다.
                Future<Attempt> contenderFuture = executor.submit(
                        () -> attempt(() -> creatorApprovalService.review(
                                creatorId, contenderId, APPROVE_REQUEST)));

                // 단순 스레드 지연이 아니라 실제 PostgreSQL 잠금 대기인지 확인한다.
                awaitRowLock(jdbcTemplate, contenderFuture, "p_creators");

                // 선행 승인을 커밋시켜 후행 요청이 변경된 승인 상태를 다시 확인하게 한다.
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

    private TestData savePendingCreatorWithAdmins() {
        // 작업 스레드가 미커밋 데이터를 기다리지 않도록 준비 데이터를 먼저 커밋한다.
        return transaction().execute(status -> {
            User master = createUser(
                    "approval-race-master@example.com",
                    "approval-race-master",
                    "010-2000-0001",
                    UserRole.MASTER);
            User manager = User.createManager(
                    uuidGenerator.generate(),
                    "approval-race-manager@example.com",
                    "encoded-password",
                    "approval-race-manager",
                    "010-2000-0002",
                    "서울시 예시구",
                    null,
                    master.getId(),
                    CREATED_AT);
            User creatorUser = createUser(
                    "approval-race-creator@example.com",
                    "approval-race-creator",
                    "010-2000-0003",
                    UserRole.CREATOR);
            userRepository.save(master);
            userRepository.save(manager);
            userRepository.save(creatorUser);

            Creator creator = Creator.createPending(
                    uuidGenerator.generate(),
                    creatorUser.getId(),
                    "동시승인상점",
                    "200-00-00001",
                    CREATED_AT);
            creatorRepository.save(creator);

            return new TestData(master.getId(), manager.getId(), creator.getId());
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

    private Creator findCreator(UUID creatorId) {
        return transaction().execute(status ->
                creatorRepository.findActiveById(creatorId).orElseThrow());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private Attempt attempt(Supplier<CreatorApprovalResponse> action) {
        try {
            return new Attempt(action.get(), null);
        } catch (RuntimeException exception) {
            // Future 예외로 감싸지 않고 경쟁 요청의 도메인 결과로 수집한다.
            return new Attempt(null, exception);
        }
    }

    private record TestData(UUID masterId, UUID managerId, UUID creatorId) {

        private UUID adminId(UserRole role) {
            return role == UserRole.MASTER ? masterId : managerId;
        }

        private UUID otherAdminId(UserRole role) {
            return role == UserRole.MASTER ? managerId : masterId;
        }
    }

    private record Attempt(CreatorApprovalResponse value, RuntimeException failure) {
    }

    private record RaceResult(Attempt winner, Attempt contender) {
    }
}
