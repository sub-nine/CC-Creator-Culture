package com.sub9.userservice.auth.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.auth.application.service.SignupService;
import com.sub9.userservice.auth.presentation.request.CreatorSignupRequest;
import com.sub9.userservice.auth.presentation.request.CustomerSignupRequest;
import com.sub9.userservice.auth.presentation.response.CreatorSignupResponse;
import com.sub9.userservice.auth.presentation.response.CustomerSignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/signup")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;

    @PostMapping("/customer")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerSignupResponse> signupCustomer(
            @Valid @RequestBody CustomerSignupRequest request) {
        return ApiResponse.success("회원가입이 완료되었습니다.", signupService.signupCustomer(request));
    }

    @PostMapping("/creator")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatorSignupResponse> signupCreator(
            @Valid @RequestBody CreatorSignupRequest request) {
        return ApiResponse.success("회원가입이 완료되었습니다.", signupService.signupCreator(request));
    }
}
