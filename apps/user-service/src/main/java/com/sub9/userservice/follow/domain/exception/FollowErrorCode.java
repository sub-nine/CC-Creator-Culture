package com.sub9.userservice.follow.domain.exception;

import com.sub9.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FollowErrorCode implements ErrorCode {
    FOLLOW_TARGET_NOT_FOUND(
            "FOLLOW_0001", HttpStatus.NOT_FOUND, "팔로우 가능한 크리에이터를 찾을 수 없습니다."),
    FOLLOW_ALREADY_EXISTS(
            "FOLLOW_0002", HttpStatus.CONFLICT, "이미 팔로우 중인 크리에이터입니다."),
    FOLLOW_NOT_FOUND(
            "FOLLOW_0003", HttpStatus.NOT_FOUND, "팔로우 관계를 찾을 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    FollowErrorCode(String code, HttpStatus status, String message) {
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
