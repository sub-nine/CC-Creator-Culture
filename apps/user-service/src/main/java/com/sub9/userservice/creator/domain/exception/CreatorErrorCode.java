package com.sub9.userservice.creator.domain.exception;

import com.sub9.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CreatorErrorCode implements ErrorCode {
    CREATOR_NOT_FOUND("CREATOR_0001", HttpStatus.NOT_FOUND, "창작자를 찾을 수 없습니다."),
    CREATOR_ALREADY_REVIEWED(
            "CREATOR_0002", HttpStatus.CONFLICT, "이미 심사가 완료된 창작자입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    CreatorErrorCode(String code, HttpStatus status, String message) {
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
