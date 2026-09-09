package com.sub9.userservice.notification.application.exception;

import com.sub9.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND(
            "NOTIFICATION_0001",
            HttpStatus.NOT_FOUND,
            "Notification was not found.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    NotificationErrorCode(String code, HttpStatus status, String
            message) {
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