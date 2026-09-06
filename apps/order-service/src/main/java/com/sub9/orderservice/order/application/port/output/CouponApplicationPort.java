package com.sub9.orderservice.order.application.port.output;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.util.List;
import java.util.UUID;

public interface CouponApplicationPort {

    List<AppliedCoupon> apply(UUID customerId, List<CouponApplicationRequest> requests);

    record CouponApplicationRequest(
            UUID cartItemId,
            UUID productId,
            UUID skuId,
            UUID userCouponId,
            long originalAmount
        ) {

        public CouponApplicationRequest {
            requireIdentifier(cartItemId);
            requireIdentifier(productId);
            requireIdentifier(skuId);
            requireIdentifier(userCouponId);
            if (originalAmount < 0) {
                throw invalidAmount();
            }
        }
    }

    record AppliedCoupon(UUID cartItemId, UUID userCouponId, long discountAmount) {

        public AppliedCoupon {
            requireIdentifier(cartItemId);
            requireIdentifier(userCouponId);
            if (discountAmount < 0) {
                throw invalidAmount();
            }
        }
    }

    private static void requireIdentifier(UUID value) {
        if (value == null) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
    }

    private static BusinessException invalidAmount() {
        return new BusinessException(OrderErrorCode.INVALID_ORDER_AMOUNT);
    }
}
