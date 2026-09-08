package com.sub9.orderservice.coupon.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 발급 쿠폰 조회")
class UserCouponQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    private final UuidV7Generator generator = new UuidV7Generator();
    @Mock private UserCouponRepository userCouponRepository;
    @Mock private Clock clock;
    @InjectMocks private UserCouponQueryService service;

    @Test
    @DisplayName("사용자와 상태로 쿠폰을 조회하고 만료 여부를 같은 시각으로 계산한다")
    void finds_user_coupons_and_calculates_expiration() {
        UUID userId = generator.generate();
        var pageable = PageRequest.of(0, 20);
        UserCoupon expired = issuedCoupon(userId, NOW.minusSeconds(1));
        UserCoupon active = issuedCoupon(userId, NOW.plusSeconds(1));
        given(clock.instant()).willReturn(NOW);
        given(userCouponRepository.findAllByUserId(userId, UserCouponStatus.ISSUED, pageable))
                .willReturn(new PageImpl<>(List.of(expired, active)));

        var result = service.findAll(userId, UserCouponStatus.ISSUED, pageable);

        assertThat(result.getContent())
                .extracting(response -> response.expired())
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("사용된 쿠폰은 만료 시각이 지나도 만료 상태로 표시하지 않는다")
    void used_coupon_is_not_marked_as_expired() {
        UUID userId = generator.generate();
        var pageable = PageRequest.of(0, 20);
        UserCoupon used = issuedCoupon(userId, NOW.minusSeconds(1));
        used.use(userId, generator.generate(), NOW.minusSeconds(2));
        given(clock.instant()).willReturn(NOW);
        given(userCouponRepository.findAllByUserId(userId, null, pageable))
                .willReturn(new PageImpl<>(List.of(used)));

        var result = service.findAll(userId, null, pageable);

        assertThat(result.getContent().getFirst().expired()).isFalse();
        assertThat(result.getContent().getFirst().status()).isEqualTo(UserCouponStatus.USED);
    }

    private UserCoupon issuedCoupon(UUID userId, Instant expiredAt) {
        Coupon coupon = Coupon.create(
                generator.generate(), "사용자 쿠폰", 10, 10,
                NOW.minusSeconds(60), expiredAt, generator.generate(), NOW.minusSeconds(120));
        return UserCoupon.issue(generator.generate(), coupon, userId, NOW.minusSeconds(30));
    }
}
