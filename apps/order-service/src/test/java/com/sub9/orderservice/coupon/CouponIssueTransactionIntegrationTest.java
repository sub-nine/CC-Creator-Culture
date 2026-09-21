package com.sub9.orderservice.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.dto.CouponIssueFailureType;
import com.sub9.orderservice.coupon.application.port.CouponIssueProcessor;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.infrastructure.persistence.CouponIssueFailureClassifier;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.order.application.port.output.StockPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@MockitoBean(types = {
    CartQueryService.class,
    CouponApplicationPort.class,
    CouponUsagePort.class,
    StockPort.class,
    PaymentCancellationPort.class
})
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
        "management.tracing.export.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("쿠폰 발급 PostgreSQL 트랜잭션")
class CouponIssueTransactionIntegrationTest {
    private static final Instant ISSUE_TIME = Instant.parse("2026-09-06T00:00:00Z");
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("coupon_issue_test").withUsername("test").withPassword("test");

    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponIssueProcessor couponIssueProcessor;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CouponIssueFailureClassifier failureClassifier;
    @MockitoBean private Clock clock;
    private final UuidV7Generator generator = new UuidV7Generator();

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(ISSUE_TIME);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from p_user_coupons");
        jdbcTemplate.update("delete from p_coupons");
    }

    @Test
    @DisplayName("쿠폰 수량을 갱신하지 않고 사용자 쿠폰을 커밋한다")
    void when_issue_succeeds_only_user_coupon_is_committed() {
        Coupon coupon = saveIssuableCoupon(2);
        UUID userId = generator.generate();

        UUID userCouponId = couponIssueProcessor.process(
                new CouponReservation(coupon.getId(), userId, generator.generate()));

        Coupon updated = couponRepository.findActiveById(coupon.getId()).orElseThrow();
        assertThat(updated.getIssuedQuantity()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where id = ?", Integer.class, userCouponId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 쿠폰 중복 저장이 실패하면 발급 이력이 추가되지 않는다")
    void when_user_coupon_insert_fails_issue_history_is_not_added() {
        Coupon coupon = saveIssuableCoupon(3);
        UUID userId = generator.generate();
        couponIssueProcessor.process(new CouponReservation(coupon.getId(), userId, generator.generate()));

        RuntimeException failure = catchThrowableOfType(RuntimeException.class, () ->
                couponIssueProcessor.process(
                        new CouponReservation(coupon.getId(), userId, generator.generate())));

        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(failureClassifier.classify(failure))
                .isEqualTo(CouponIssueFailureType.ALREADY_ISSUED);

        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity())
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ?", Integer.class, coupon.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰이 없으면 사용자 쿠폰을 저장하지 않는다")
    void when_coupon_does_not_exist_nothing_is_persisted() {
        UUID couponId = generator.generate();

        assertThatThrownBy(() -> couponIssueProcessor.process(
                new CouponReservation(couponId, generator.generate(), generator.generate())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ?", Integer.class, couponId))
                .isZero();
    }

    private Coupon saveIssuableCoupon(int totalQuantity) {
        return couponRepository.save(Coupon.create(
                generator.generate(), "발급 쿠폰", 10, totalQuantity,
                ISSUE_TIME.minusSeconds(60), ISSUE_TIME.plusSeconds(60),
                generator.generate(), ISSUE_TIME.minusSeconds(120)));
    }
}
