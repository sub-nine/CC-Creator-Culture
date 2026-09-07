package com.sub9.orderservice.coupon.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.coupon.application.service.UserCouponQueryService;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import com.sub9.orderservice.coupon.presentation.response.UserCouponResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-coupons")
@RequiredArgsConstructor
public class UserCouponQueryController {

    private final UserCouponQueryService userCouponQueryService;

    @GetMapping
    public ApiResponse<Page<UserCouponResponse>> findAll(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) UserCouponStatus status,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(userCouponQueryService.findAll(userId, status, pageable));
    }
}
