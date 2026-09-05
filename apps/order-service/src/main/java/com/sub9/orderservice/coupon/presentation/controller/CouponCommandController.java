package com.sub9.orderservice.coupon.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.coupon.application.service.CouponCommandService;
import com.sub9.orderservice.coupon.presentation.request.CreateCouponRequest;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponResponse> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateCouponRequest request) {
        CouponResponse response = couponCommandService.create(request, userId);
        log.info("[쿠폰 관리][생성][완료] couponId={}", response.couponId());
        return ApiResponse.success("쿠폰이 생성되었습니다.", response);
    }
}
