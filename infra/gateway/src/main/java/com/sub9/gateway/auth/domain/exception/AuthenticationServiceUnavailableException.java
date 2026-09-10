package com.sub9.gateway.auth.domain.exception;

public class AuthenticationServiceUnavailableException extends RuntimeException {
    // 서버가 블랙리스트 상태를 확인할 수 없음
    // 503 / COMMON_0009

    private static final String MESSAGE = "인증 서비스를 일시적으로 사용할 수 없습니다.";

    public AuthenticationServiceUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
