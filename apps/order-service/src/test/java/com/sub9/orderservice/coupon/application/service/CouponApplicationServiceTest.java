package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.CouponApplicationRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("주문 쿠폰 적용")
class CouponApplicationServiceTest {
    private static final Instant STARTED_AT = Instant.parse("2026-09-08T00:00:00Z");
    private final UuidV7Generator generator = new UuidV7Generator();
    private final UUID customerId = generator.generate();
    @Mock private UserCouponRepository repository;

    @Test
    @DisplayName("발급량이 소진되어도 소유 쿠폰의 원 미만 할인을 버리고 사용 상태는 유지한다")
    void when_issued_quantity_is_exhausted_discount_is_returned_without_using_coupon() {
        UserCoupon coupon = storedCoupon(20);
        coupon.getCoupon().issue(customerId, STARTED_AT);
        var request = request(coupon.getId(), 10_009);

        var result = service(0).apply(customerId, List.of(request));

        assertThat(result).containsExactly(new AppliedCoupon(request.cartItemId(), coupon.getId(), 2_001));
        assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(coupon.getUsedAt()).isNull();
        assertThat(coupon.getOrderId()).isNull();
    }

    @Test
    @DisplayName("보유 쿠폰이 없으면 쿠폰 없음 오류를 반환한다")
    void when_user_coupon_does_not_exist_not_found_error_is_thrown() {
        UUID missingId = generator.generate();
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        assertError(() -> service(0).apply(customerId, List.of(request(missingId, 100))),
                CouponErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 쿠폰을 적용할 수 없다")
    void when_customer_is_not_owner_coupon_application_is_rejected() {
        UserCoupon coupon = storedCoupon(20);
        assertError(() -> service(0).apply(generator.generate(), List.of(request(coupon.getId(), 100))),
                CouponErrorCode.COUPON_NOT_USABLE);
    }

    @Test
    @DisplayName("이미 사용된 쿠폰을 적용할 수 없다")
    void when_coupon_is_used_coupon_application_is_rejected() {
        UserCoupon coupon = storedCoupon(20);
        coupon.use(customerId, generator.generate(), STARTED_AT);
        assertError(() -> service(0).apply(customerId, List.of(request(coupon.getId(), 100))),
                CouponErrorCode.COUPON_NOT_USABLE);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 3_601})
    @DisplayName("시작 전과 만료 후에는 쿠폰을 적용할 수 없다")
    void when_time_is_outside_period_coupon_application_is_rejected(long offset) {
        UserCoupon coupon = storedCoupon(20);
        assertError(() -> service(offset).apply(customerId, List.of(request(coupon.getId(), 100))),
                CouponErrorCode.COUPON_NOT_USABLE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 3_600})
    @DisplayName("시작 시각과 만료 시각에는 쿠폰을 적용할 수 있다")
    void when_time_is_on_period_boundary_coupon_application_succeeds(long offset) {
        UserCoupon coupon = storedCoupon(20);
        assertThat(service(offset).apply(customerId, List.of(request(coupon.getId(), 100))))
                .extracting(AppliedCoupon::discountAmount).containsExactly(20L);
    }

    @Test
    @DisplayName("삭제된 쿠폰을 적용할 수 없다")
    void when_coupon_is_deleted_coupon_application_is_rejected() {
        UserCoupon coupon = storedCoupon(20);
        coupon.getCoupon().delete(customerId, STARTED_AT);
        assertError(() -> service(0).apply(customerId, List.of(request(coupon.getId(), 100))),
                CouponErrorCode.COUPON_NOT_USABLE);
    }

    @Test
    @DisplayName("같은 보유 쿠폰을 여러 주문 항목에 중복 적용할 수 없다")
    void when_user_coupon_is_repeated_coupon_application_is_rejected() {
        UserCoupon coupon = coupon(20);
        lenient().when(repository.findById(coupon.getId())).thenReturn(Optional.of(coupon));
        assertError(() -> service(0).apply(customerId, List.of(
                request(coupon.getId(), 100), request(coupon.getId(), 200))),
                CouponErrorCode.COUPON_NOT_USABLE);
    }

    @Test
    @DisplayName("최대 정수 금액도 곱셈 오버플로 없이 할인 금액을 계산한다")
    void when_original_amount_is_long_max_discount_is_calculated_without_overflow() {
        UserCoupon coupon = storedCoupon(99);
        assertThat(service(0).apply(customerId, List.of(request(coupon.getId(), Long.MAX_VALUE))))
                .extracting(AppliedCoupon::discountAmount).containsExactly(9_131_138_316_486_228_048L);
    }

    private UserCoupon storedCoupon(int rate) {
        UserCoupon coupon = coupon(rate);
        when(repository.findById(coupon.getId())).thenReturn(Optional.of(coupon));
        return coupon;
    }

    private UserCoupon coupon(int rate) {
        Coupon coupon = Coupon.create(generator.generate(), "주문 할인 쿠폰", rate, 1,
                STARTED_AT, STARTED_AT.plusSeconds(3_600), customerId, STARTED_AT.minusSeconds(1));
        return UserCoupon.issue(generator.generate(), coupon, customerId, STARTED_AT);
    }

    private CouponApplicationRequest request(UUID userCouponId, long amount) {
        return new CouponApplicationRequest(generator.generate(), generator.generate(), generator.generate(),
                userCouponId, amount);
    }

    private CouponApplicationService service(long offset) {
        return new CouponApplicationService(repository, Clock.fixed(STARTED_AT.plusSeconds(offset), ZoneOffset.UTC));
    }

    private void assertError(Runnable action, CouponErrorCode errorCode) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
