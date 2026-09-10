package com.sub9.gateway.auth.domain.exception;

public class InvalidAccessTokenException extends RuntimeException {
    // 클라이언트 인증 문제 401

    private static final String MESSAGE = "유효하지 않은 인증 토큰입니다.";

    public InvalidAccessTokenException() {
        super(MESSAGE);
    }
}
