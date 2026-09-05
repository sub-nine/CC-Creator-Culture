package com.sub9.orderservice.coupon.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.coupon.application.service.CouponQueryService;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponQueryController {
    private final CouponQueryService couponQueryService;

    @GetMapping
    public ApiResponse<Page<CouponResponse>> findAll(
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(couponQueryService.findAll(pageable));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponResponse> findById(@PathVariable UUID couponId) {
        return ApiResponse.success(couponQueryService.findById(couponId));
    }
}
