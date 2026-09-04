package com.sub9.userservice.auth.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.application.service.TokenReissueService;
import com.sub9.userservice.auth.presentation.request.TokenReissueRequest;
import com.sub9.userservice.auth.presentation.response.TokenReissueResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
public class TokenReissueController {

    private final TokenReissueService tokenReissueService;

    @PostMapping("/reissue")
    public ApiResponse<TokenReissueResponse> reissue(
            @Valid @RequestBody TokenReissueRequest request) {
        return ApiResponse.success(
                "Access Token이 재발급되었습니다.", tokenReissueService.reissue(request));
    }
}
