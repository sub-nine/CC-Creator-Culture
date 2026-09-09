package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.CouponApplicationRequest;
import com.sub9.orderservice.order.application.port.output.CouponUsagePort;
import com.sub9.orderservice.order.application.service.OrderExpirationTransactionService;
import com.sub9.orderservice.order.application.service.OrderPaymentResultService;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "management.tracing.export.enabled=false", "order.expiration.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("주문 쿠폰 실제 PostgreSQL 연동")
class CouponOrderIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private final UuidV7Generator ids = new UuidV7Generator();
    @Autowired CouponApplicationPort application;
    @Autowired CouponUsagePort usage;
    @Autowired CouponRepository coupons;
    @Autowired UserCouponRepository userCoupons;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentResultService payments;
    @Autowired OrderExpirationTransactionService expiration;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @MockitoBean Clock clock;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    @DisplayName("쿠폰 사용과 복구는 주문 트랜잭션 없이 실행할 수 없다")
    void when_transaction_is_missing_coupon_mutation_is_rejected() {
        assertThatThrownBy(() -> usage.markUsed(ids.generate(), List.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> usage.restore(ids.generate(), List.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("동일 쿠폰을 동시에 사용하는 두 주문 중 하나만 성공한다")
    void when_two_orders_use_same_coupon_concurrently_only_one_succeeds() throws Exception {
        UserCoupon coupon = issuedCoupon();
        List<AppliedCoupon> applied = apply(coupon);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(1, 2).stream().map(index -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    transaction().executeWithoutResult(status -> usage.markUsed(ids.generate(), applied));
                    return true;
                } catch (BusinessException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo(CouponErrorCode.COUPON_NOT_USABLE);
                    return false;
                }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(attempts.get(0).get(15, TimeUnit.SECONDS),
                    attempts.get(1).get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(couponStatus(coupon)).isEqualTo("USED");
    }

    @Test
    @DisplayName("검증 이후 만료된 쿠폰은 최종 사용 단계에서 거부한다")
    void when_coupon_expires_after_application_usage_is_rejected() {
        UserCoupon coupon = issuedCoupon();
        List<AppliedCoupon> applied = apply(coupon);
        when(clock.instant()).thenReturn(NOW.plusSeconds(3_601));
        assertThatThrownBy(() -> transaction().executeWithoutResult(
                status -> usage.markUsed(ids.generate(), applied)))
                .isInstanceOf(BusinessException.class);
        assertThat(couponStatus(coupon)).isEqualTo("ISSUED");
    }

    @Test
    @DisplayName("다른 주문은 쿠폰을 복구하지 못하며 같은 주문의 복구는 반복해도 안전하다")
    void when_coupon_is_restored_only_matching_order_changes_usage() {
        UserCoupon coupon = issuedCoupon();
        UUID orderId = ids.generate();
        List<AppliedCoupon> applied = apply(coupon);
        transaction().executeWithoutResult(status -> usage.markUsed(orderId, applied));
        transaction().executeWithoutResult(status -> usage.restore(ids.generate(), List.of(coupon.getId())));
        assertThat(couponStatus(coupon)).isEqualTo("USED");
        transaction().executeWithoutResult(status -> {
            usage.restore(orderId, List.of(coupon.getId()));
            usage.restore(orderId, List.of(coupon.getId()));
        });
        assertThat(couponStatus(coupon)).isEqualTo("ISSUED");
        var restored = userCoupons.findById(coupon.getId()).orElseThrow();
        assertThat(restored.getUsedAt()).isNull();
        assertThat(restored.getOrderId()).isNull();
        assertThat(restored.getUpdatedBy()).isEqualTo(coupon.getUserId());
        assertThat(restored.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("주문 저장 트랜잭션이 실패하면 쿠폰 사용도 함께 롤백된다")
    void when_order_transaction_fails_coupon_usage_is_rolled_back() {
        UserCoupon coupon = issuedCoupon();
        Order order = order(coupon);
        List<AppliedCoupon> applied = apply(coupon);
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            usage.markUsed(order.getId(), applied);
            orders.save(order);
            entityManager.flush();
            throw new IllegalStateException("저장 후 실패 재현");
        })).isInstanceOf(IllegalStateException.class).hasMessage("저장 후 실패 재현");
        assertThat(couponStatus(coupon)).isEqualTo("ISSUED");
        assertThat(jdbc.queryForObject("select count(*) from p_orders where id = ?", Long.class, order.getId()))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("결제 실패 또는 만료 시 주문 상태와 쿠폰 복구를 함께 저장한다")
    void when_order_fails_or_expires_order_status_and_coupon_are_saved(boolean expire) {
        UserCoupon coupon = issuedCoupon();
        Order order = order(coupon);
        List<AppliedCoupon> applied = apply(coupon);
        transaction().executeWithoutResult(status -> {
            usage.markUsed(order.getId(), applied);
            orders.save(order);
        });
        if (expire) {
            assertThat(expiration.expire(order.getId(), NOW.plusSeconds(600))).isPresent();
        } else {
            payments.markPaymentFailed(order.getId(), NOW.plusSeconds(1));
        }
        assertThat(jdbc.queryForObject("select status from p_orders where id = ?", String.class, order.getId()))
                .isEqualTo(expire ? "EXPIRED" : "FAILED");
        assertThat(couponStatus(coupon)).isEqualTo("ISSUED");
    }

    private UserCoupon issuedCoupon() {
        UUID owner = ids.generate();
        Coupon coupon = Coupon.create(ids.generate(), "주문 쿠폰", 10, 10,
                NOW.minusSeconds(60), NOW.plusSeconds(3_600), owner, NOW.minusSeconds(60));
        UserCoupon userCoupon = UserCoupon.issue(ids.generate(), coupon, owner, NOW);
        transaction().executeWithoutResult(status -> {
            coupons.save(coupon);
            userCoupons.save(userCoupon);
        });
        return userCoupon;
    }

    private List<AppliedCoupon> apply(UserCoupon coupon) {
        return application.apply(coupon.getUserId(), List.of(new CouponApplicationRequest(
                ids.generate(), ids.generate(), ids.generate(), coupon.getId(), 10_000)));
    }

    private Order order(UserCoupon coupon) {
        return Order.create(ids.generate(), coupon.getUserId(),
                ShippingAddress.of("홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "101호"),
                List.of(OrderItem.create(ids.generate(), ids.generate(), ids.generate(), ids.generate(),
                        coupon.getId(), ProductSnapshot.of("상품", "옵션", Money.won(10_000), 1),
                        Money.won(1_000))), NOW);
    }

    private String couponStatus(UserCoupon coupon) {
        return jdbc.queryForObject("select status from p_user_coupons where id = ?", String.class, coupon.getId());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
