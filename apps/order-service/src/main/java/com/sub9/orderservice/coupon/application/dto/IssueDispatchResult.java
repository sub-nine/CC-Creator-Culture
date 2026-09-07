package com.sub9.orderservice.coupon.application.dto;

import java.util.Objects;
import java.util.UUID;

public sealed interface IssueDispatchResult {
    // 동기 처리 완료와 향후 비동기 접수 결과

    record Completed(UUID userCouponId) implements IssueDispatchResult {

        public Completed {
            Objects.requireNonNull(userCouponId, "사용자 쿠폰 식별자는 필수입니다.");
        }
    }

    record Accepted(UUID requestId) implements IssueDispatchResult {

        public Accepted {
            Objects.requireNonNull(requestId, "발급 요청 식별자는 필수입니다.");
        }
    }
}
