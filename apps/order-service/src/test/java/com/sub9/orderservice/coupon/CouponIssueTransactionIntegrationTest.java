package com.sub9.orderservice.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.application.dto.CouponReservation;
import com.sub9.orderservice.coupon.application.port.CouponIssueProcessor;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
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
    @DisplayName("수량 증가와 사용자 쿠폰 저장을 함께 커밋한다")
    void when_issue_succeeds_quantity_and_user_coupon_are_committed_together() {
        Coupon coupon = saveIssuableCoupon(2);
        UUID userId = generator.generate();

        UUID userCouponId = couponIssueProcessor.process(
                new CouponReservation(coupon.getId(), userId, generator.generate()));

        Coupon updated = couponRepository.findActiveById(coupon.getId()).orElseThrow();
        assertThat(updated.getIssuedQuantity()).isEqualTo(1);
        assertThat(updated.getUpdatedAt()).isEqualTo(ISSUE_TIME);
        assertThat(updated.getUpdatedBy()).isEqualTo(userId);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where id = ?", Integer.class, userCouponId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 쿠폰 중복 저장이 실패하면 두 번째 수량 증가도 롤백한다")
    void when_user_coupon_insert_fails_quantity_increase_is_rolled_back() {
        Coupon coupon = saveIssuableCoupon(3);
        UUID userId = generator.generate();
        couponIssueProcessor.process(new CouponReservation(coupon.getId(), userId, generator.generate()));

        assertThatThrownBy(() -> couponIssueProcessor.process(
                new CouponReservation(coupon.getId(), userId, generator.generate())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity())
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ?", Integer.class, coupon.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("발급 기간 밖이면 수량과 사용자 쿠폰을 변경하지 않는다")
    void when_coupon_is_outside_period_nothing_is_persisted() {
        Coupon coupon = Coupon.create(generator.generate(), "종료 쿠폰", 10, 2,
                ISSUE_TIME.minusSeconds(60), ISSUE_TIME.minusSeconds(1),
                generator.generate(), ISSUE_TIME.minusSeconds(120));
        couponRepository.save(coupon);

        assertThatThrownBy(() -> couponIssueProcessor.process(
                new CouponReservation(coupon.getId(), generator.generate(), generator.generate())))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CouponErrorCode.NOT_IN_ISSUE_PERIOD));
        assertThat(couponRepository.findActiveById(coupon.getId()).orElseThrow().getIssuedQuantity()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from p_user_coupons where coupon_id = ?", Integer.class, coupon.getId()))
                .isZero();
    }

    private Coupon saveIssuableCoupon(int totalQuantity) {
        return couponRepository.save(Coupon.create(
                generator.generate(), "발급 쿠폰", 10, totalQuantity,
                ISSUE_TIME.minusSeconds(60), ISSUE_TIME.plusSeconds(60),
                generator.generate(), ISSUE_TIME.minusSeconds(120)));
    }
}
