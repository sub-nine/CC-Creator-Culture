package com.sub9.gateway.auth.domain.exception;

public class InvalidAccessTokenException extends RuntimeException {

    private static final String MESSAGE = "유효하지 않은 인증 토큰입니다.";

    public InvalidAccessTokenException() {
        super(MESSAGE);
    }
}
