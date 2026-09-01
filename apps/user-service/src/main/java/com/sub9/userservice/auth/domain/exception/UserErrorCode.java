package com.sub9.userservice.auth.domain.exception;

import com.sub9.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS("USER_0001", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS("USER_0002", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    PHONE_ALREADY_EXISTS("USER_0003", HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),
    CREATOR_NAME_ALREADY_EXISTS("USER_0004", HttpStatus.CONFLICT, "이미 사용 중인 상호명입니다."),
    BUSINESS_REGISTRATION_NUMBER_ALREADY_EXISTS(
            "USER_0005", HttpStatus.CONFLICT, "이미 사용 중인 사업자등록번호입니다."),
    SIGNUP_VALUE_ALREADY_EXISTS("USER_0006", HttpStatus.CONFLICT, "이미 사용 중인 가입 정보입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    UserErrorCode(String code, HttpStatus status, String message) {
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
