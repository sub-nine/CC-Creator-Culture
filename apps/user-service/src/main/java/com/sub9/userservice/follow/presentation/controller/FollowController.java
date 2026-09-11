package com.sub9.userservice.follow.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.infrastructure.security.GatewayAuthenticationPrincipal;
import com.sub9.userservice.follow.application.service.FollowService;
import com.sub9.userservice.follow.presentation.response.FollowStatusResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{creatorId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FollowStatusResponse> follow(
            @PathVariable UUID creatorId,
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal) {
        return ApiResponse.success(
                "크리에이터를 팔로우했습니다.",
                followService.follow(principal.userId(), creatorId));
    }

    @DeleteMapping("/{creatorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(
            @PathVariable UUID creatorId,
            @AuthenticationPrincipal GatewayAuthenticationPrincipal principal) {
        followService.unfollow(principal.userId(), creatorId);
    }
}
