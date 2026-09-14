package com.sub9.userservice.user.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.infrastructure.security.GatewayAuthenticationPrincipal;
import com.sub9.userservice.user.application.service.UserProfileService;
import com.sub9.userservice.user.presentation.request.UpdateMyProfileRequest;
import com.sub9.userservice.user.presentation.response.MyProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ApiResponse<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal) {
        return ApiResponse.success(
                "내 정보를 조회했습니다.",
                userProfileService.getMyProfile(principal.userId()));
    }

    @PatchMapping("/me")
    public ApiResponse<MyProfileResponse> updateMyProfile(
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
            @Valid @RequestBody UpdateMyProfileRequest request) {
        return ApiResponse.success(
                "내 정보를 수정했습니다.",
                userProfileService.updateMyProfile(principal.userId(), request));
    }
}
