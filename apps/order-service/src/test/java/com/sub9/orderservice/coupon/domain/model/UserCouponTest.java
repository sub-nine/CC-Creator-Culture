package com.sub9.orderservice.coupon.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사용자 쿠폰 도메인 모델")
class UserCouponTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-01T11:00:01Z");
    private static final Instant EXPIRED_AT = Instant.parse("2026-09-03T14:59:59Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("쿠폰을 발급하면 ISSUED 상태와 소유자 감사 정보를 기록한다")
    void when_coupon_is_issued_user_coupon_and_audit_are_initialized() {
        UUID couponId = uuidGenerator.generate();
        UUID userId = uuidGenerator.generate();

        UserCoupon userCoupon = issue(couponId, userId);

        assertThat(userCoupon.getCouponId()).isEqualTo(couponId);
        assertThat(userCoupon.getUserId()).isEqualTo(userId);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(userCoupon.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(userCoupon.getUsedAt()).isNull();
        assertThat(userCoupon.getOrderId()).isNull();
        assertThat(userCoupon.getCreatedAt()).isEqualTo(ISSUED_AT);
        assertThat(userCoupon.getCreatedBy()).isEqualTo(userId);
        assertThat(userCoupon.getUpdatedAt()).isEqualTo(ISSUED_AT);
        assertThat(userCoupon.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("소유자가 쿠폰을 사용하면 USED 상태와 주문 및 수정 감사를 기록한다")
    void when_owner_uses_coupon_status_order_and_update_audit_are_changed() {
        UUID userId = uuidGenerator.generate();
        UUID orderId = uuidGenerator.generate();
        Instant usedAt = ISSUED_AT.plusSeconds(60);
        UserCoupon userCoupon = issue(uuidGenerator.generate(), userId);

        userCoupon.use(userId, orderId, usedAt);

        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        assertThat(userCoupon.getOrderId()).isEqualTo(orderId);
        assertThat(userCoupon.getUsedAt()).isEqualTo(usedAt);
        assertThat(userCoupon.getUpdatedBy()).isEqualTo(userId);
        assertThat(userCoupon.getUpdatedAt()).isEqualTo(usedAt);
    }

    @Test
    @DisplayName("이미 사용한 쿠폰을 다시 사용할 수 없다")
    void when_used_coupon_is_used_again_state_transition_is_rejected() {
        UUID userId = uuidGenerator.generate();
        UserCoupon userCoupon = issue(uuidGenerator.generate(), userId);
        userCoupon.use(userId, uuidGenerator.generate(), ISSUED_AT.plusSeconds(60));

        assertThatThrownBy(() -> userCoupon.use(
                userId, uuidGenerator.generate(), ISSUED_AT.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 사용된 쿠폰입니다.");
    }

    @Test
    @DisplayName("쿠폰 소유자가 아니면 사용할 수 없다")
    void when_non_owner_uses_coupon_usage_is_rejected() {
        UserCoupon userCoupon = issue(uuidGenerator.generate(), uuidGenerator.generate());

        assertThatThrownBy(() -> userCoupon.use(
                uuidGenerator.generate(), uuidGenerator.generate(), ISSUED_AT.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("쿠폰 소유자만 사용할 수 있습니다.");
    }

    @Test
    @DisplayName("필수 식별자와 발급 시각이 없으면 사용자 쿠폰을 생성할 수 없다")
    void when_required_issue_value_is_missing_user_coupon_creation_is_rejected() {
        UUID id = uuidGenerator.generate();
        UUID couponId = uuidGenerator.generate();
        UUID userId = uuidGenerator.generate();

        assertThatThrownBy(() -> UserCoupon.issue(id, null, userId, ISSUED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UserCoupon.issue(id, coupon(couponId), null, ISSUED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UserCoupon.issue(id, coupon(couponId), userId, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UUID v7이 아닌 사용자 쿠폰 식별자를 거부한다")
    void when_user_coupon_id_is_not_uuid_v7_creation_is_rejected() {
        assertThatThrownBy(() -> UserCoupon.issue(
                UUID.randomUUID(), coupon(uuidGenerator.generate()), uuidGenerator.generate(), ISSUED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("식별자는 UUID v7 형식이어야 합니다.");
    }

    private UserCoupon issue(UUID couponId, UUID userId) {
        return UserCoupon.issue(uuidGenerator.generate(), coupon(couponId), userId, ISSUED_AT);
    }

    private Coupon coupon(UUID couponId) {
        return Coupon.create(
                couponId, "쿠폰", 10, 100,
                ISSUED_AT.minusSeconds(60), EXPIRED_AT,
                uuidGenerator.generate(), ISSUED_AT.minusSeconds(60));
    }
}
