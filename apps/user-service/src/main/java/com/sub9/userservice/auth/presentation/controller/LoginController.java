package com.sub9.userservice.auth.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.application.service.LoginService;
import com.sub9.userservice.auth.presentation.request.LoginRequest;
import com.sub9.userservice.auth.presentation.response.LoginResponse;
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
// JWT 비밀키가 설정된 환경에서만 로그인 API를 활성화한다.
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인이 완료되었습니다.", loginService.login(request));
    }
}
