package com.sub9.gateway.auth.infrastructure.web;

import org.springframework.http.HttpStatus;

// // WebFlux Gateway가 MVC 기반 libs/common에 의존하지 않도록 분리한 인증 오류 코드
public enum GatewayAuthenticationErrorCode {
    UNAUTHORIZED("COMMON_0007", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    SERVICE_UNAVAILABLE(
            "COMMON_0009", HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 사용할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    GatewayAuthenticationErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
