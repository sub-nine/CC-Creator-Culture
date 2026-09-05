package com.sub9.userservice.notification.infrastructure.presentation.response;

public enum ApiErrorCode {

    NOTIFICATION_NOT_FOUND("0000", "Notification was not found."),
    MISSING_REQUIRED_VALUE("0001", "A required value is missing."),
    INVALID_REQUEST("0002", "The request value is invalid."),
    INTERNAL_SERVER_ERROR("9999", "An internal server error occurred.");

    private final String code;
    private final String message;

    ApiErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {

        return code;
    }

    public String message() {

        return message;
    }
}