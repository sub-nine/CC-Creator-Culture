package com.sub9.userservice.notification.infrastructure.presentation.response;

import java.util.List;
import java.util.Map;

public record ApiError(
        String errorCode,
        String message,
        List<Map<String, String>> errors
) {

    public ApiError {
        errors = errors == null
                ? List.of()
                : List.copyOf(errors);
    }

    public static ApiError of(ApiErrorCode errorCode) {
        return new ApiError(
                errorCode.code(),
                errorCode.message(),
                List.of());
    }

    public static ApiError of(ApiErrorCode errorCode, List<Map<String, String>> errors) {
        return new ApiError(
                errorCode.code(),
                errorCode.message(),
                errors);
    }
}