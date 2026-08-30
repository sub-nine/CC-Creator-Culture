package com.sub9.common.dto.response;

import com.sub9.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public final class ErrorResponse {

    private final String errorCode;
    private final String message;
    private final List<Map<String, String>> errors;

    private ErrorResponse(String errorCode, String message, List<Map<String, String>> errors) {
        this.errorCode = errorCode;
        this.message = message;
        this.errors = immutableErrors(errors);
    }

    public static ErrorResponse from(ErrorCode errorCode) {
        return from(errorCode, List.of());
    }

    public static ErrorResponse from(ErrorCode errorCode, List<Map<String, String>> errors) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), errors);
    }

    private static List<Map<String, String>> immutableErrors(List<Map<String, String>> errors) {
        if (errors.isEmpty()) {
            return List.of();
        }
        return errors.stream()
                .map(Map::copyOf)
                .toList();
    }
}
