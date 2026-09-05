package com.sub9.orderservice.coupon.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("쿠폰 도메인 모델")
class CouponTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-01T01:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2026-09-03T14:59:59Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("유효한 정보로 발급 수량이 0인 쿠폰을 생성한다")
    void when_valid_values_are_given_coupon_is_created_with_zero_issued_quantity() {
        UUID creatorId = uuidGenerator.generate();

        Coupon coupon = coupon(15, 100, creatorId);

        assertThat(coupon.getCouponName()).isEqualTo("트렌드 15% 할인 쿠폰");
        assertThat(coupon.getDiscountRate()).isEqualTo(15);
        assertThat(coupon.getTotalQuantity()).isEqualTo(100);
        assertThat(coupon.getIssuedQuantity()).isZero();
        assertThat(coupon.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(coupon.getCreatedBy()).isEqualTo(creatorId);
        assertThat(coupon.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(coupon.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("할인율의 최소값과 최대값을 허용한다")
    void when_discount_rate_is_boundary_value_coupon_is_created() {
        assertThat(coupon(1, 1, uuidGenerator.generate()).getDiscountRate()).isEqualTo(1);
        assertThat(coupon(100, 1, uuidGenerator.generate()).getDiscountRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("할인율 범위와 총 발급 수량 규칙을 벗어난 쿠폰 생성을 거부한다")
    void when_discount_rate_or_quantity_is_invalid_coupon_creation_is_rejected() {
        assertThatThrownBy(() -> coupon(0, 1, uuidGenerator.generate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("할인율은 1 이상 100 이하여야 합니다.");
        assertThatThrownBy(() -> coupon(101, 1, uuidGenerator.generate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("할인율은 1 이상 100 이하여야 합니다.");
        assertThatThrownBy(() -> coupon(10, 0, uuidGenerator.generate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("총 발급 수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("쿠폰 시작 시각이 만료 시각보다 빠르지 않으면 생성을 거부한다")
    void when_started_at_is_not_before_expired_at_coupon_creation_is_rejected() {
        assertThatThrownBy(() -> Coupon.create(
                uuidGenerator.generate(), "쿠폰", 10, 10,
                EXPIRED_AT, EXPIRED_AT, uuidGenerator.generate(), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("쿠폰 시작 시각은 만료 시각보다 빨라야 합니다.");
    }

    @Test
    @DisplayName("쿠폰 시작 시각과 만료 시각을 포함해 발급 가능하다")
    void when_request_is_on_period_boundary_coupon_is_issuable() {
        Coupon coupon = coupon(10, 2, uuidGenerator.generate());

        assertThat(coupon.isIssuableAt(STARTED_AT)).isTrue();
        assertThat(coupon.isIssuableAt(EXPIRED_AT)).isTrue();
        assertThat(coupon.isIssuableAt(STARTED_AT.minusNanos(1))).isFalse();
        assertThat(coupon.isIssuableAt(EXPIRED_AT.plusNanos(1))).isFalse();
    }

    @Test
    @DisplayName("발급하면 수량과 수정 감사를 갱신하고 총수량에 도달하면 품절된다")
    void when_coupon_is_issued_quantity_and_update_audit_are_changed_until_sold_out() {
        Coupon coupon = coupon(10, 1, uuidGenerator.generate());
        UUID userId = uuidGenerator.generate();
        Instant issuedAt = STARTED_AT.plusSeconds(1);

        coupon.issue(userId, issuedAt);

        assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
        assertThat(coupon.getUpdatedAt()).isEqualTo(issuedAt);
        assertThat(coupon.getUpdatedBy()).isEqualTo(userId);
        assertThat(coupon.isIssuableAt(issuedAt)).isFalse();
        assertThatThrownBy(() -> coupon.issue(uuidGenerator.generate(), issuedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("현재 발급할 수 없는 쿠폰입니다.");
    }

    @Test
    @DisplayName("삭제된 쿠폰은 발급할 수 없고 최초 삭제 감사를 유지한다")
    void when_coupon_is_deleted_it_is_not_issuable_and_first_deletion_audit_is_preserved() {
        Coupon coupon = coupon(10, 10, uuidGenerator.generate());
        UUID firstActorId = uuidGenerator.generate();
        Instant firstDeletedAt = STARTED_AT.plusSeconds(1);

        coupon.delete(firstActorId, firstDeletedAt);
        coupon.delete(uuidGenerator.generate(), firstDeletedAt.plusSeconds(1));

        assertThat(coupon.isIssuableAt(firstDeletedAt)).isFalse();
        assertThat(coupon.getDeletedBy()).isEqualTo(firstActorId);
        assertThat(coupon.getDeletedAt()).isEqualTo(firstDeletedAt);
        assertThat(coupon.getUpdatedBy()).isEqualTo(firstActorId);
        assertThat(coupon.getUpdatedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    @DisplayName("UUID v7이 아닌 쿠폰 식별자를 거부한다")
    void when_coupon_id_is_not_uuid_v7_creation_is_rejected() {
        assertThatThrownBy(() -> Coupon.create(
                UUID.randomUUID(), "쿠폰", 10, 10,
                STARTED_AT, EXPIRED_AT, uuidGenerator.generate(), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("식별자는 UUID v7 형식이어야 합니다.");
    }

    private Coupon coupon(int discountRate, int totalQuantity, UUID creatorId) {
        return Coupon.create(
                uuidGenerator.generate(), " 트렌드 15% 할인 쿠폰 ", discountRate, totalQuantity,
                STARTED_AT, EXPIRED_AT, creatorId, CREATED_AT);
    }
}
