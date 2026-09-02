package com.sub9.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INTERNAL_SERVER_ERROR("COMMON_0001", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    BAD_REQUEST("COMMON_0002", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_ERROR("COMMON_0003", HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
    RESOURCE_NOT_FOUND("COMMON_0004", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED("COMMON_0005", HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP method입니다."),
    UNSUPPORTED_MEDIA_TYPE("COMMON_0006", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    UNAUTHORIZED("COMMON_0007", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN("COMMON_0008", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    // redis 장애 시 사용
    SERVICE_UNAVAILABLE(
            "COMMON_0009", HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 사용할 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    CommonErrorCode(String code, HttpStatus status, String message) {
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
