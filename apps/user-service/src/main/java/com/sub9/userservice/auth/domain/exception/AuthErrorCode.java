package com.sub9.userservice.auth.domain.exception;

import com.sub9.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(
            "AUTH_0001", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(
            "AUTH_0002", HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    CREATOR_APPROVAL_PENDING(
            "AUTH_0003", HttpStatus.FORBIDDEN, "창작자 승인이 완료되지 않았습니다."),
    CREATOR_APPROVAL_REJECTED(
            "AUTH_0004", HttpStatus.FORBIDDEN, "창작자 승인이 거절되었습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    AuthErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
