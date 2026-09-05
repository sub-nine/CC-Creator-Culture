package com.sub9.userservice.auth.domain.exception;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("유효하지 않은 인증 토큰입니다.");
    }
}
