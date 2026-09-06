package com.sub9.orderservice.coupon.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.service.CouponCommandService;
import com.sub9.orderservice.coupon.application.service.CouponIssueService;
import com.sub9.orderservice.coupon.presentation.request.CreateCouponRequest;
import com.sub9.orderservice.coupon.presentation.response.CouponIssueResponse;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponCommandController {
    private static final Logger log = LoggerFactory.getLogger(CouponCommandController.class);
    private final CouponCommandService couponCommandService;
    private final CouponIssueService couponIssueService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponResponse> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateCouponRequest request) {
        CouponResponse response = couponCommandService.create(request, userId);
        log.info("[쿠폰 관리][생성][완료] couponId={}", response.couponId());
        return ApiResponse.success("쿠폰이 생성되었습니다.", response);
    }

    @PostMapping("/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponIssueResponse> issue(
            @PathVariable UUID couponId,
            @RequestHeader("X-User-Id") UUID userId) {
        IssueDispatchResult result = couponIssueService.issue(couponId, userId);
        if (!(result instanceof IssueDispatchResult.Completed completed)) {
            throw new IllegalStateException("동기 쿠폰 발급에서 처리 완료 결과를 받지 못했습니다.");
        }

        log.info("[쿠폰 발급][동기][API 응답 완료] couponId={} userCouponId={}",
                couponId, completed.userCouponId());
        return ApiResponse.success("쿠폰이 발급되었습니다.", CouponIssueResponse.from(completed));
    }
}
