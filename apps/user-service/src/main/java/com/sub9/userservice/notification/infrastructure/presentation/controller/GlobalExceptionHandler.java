package com.sub9.userservice.notification.infrastructure.presentation.controller;

import com.sub9.userservice.notification.application.exception.NotificationNotFoundException;
import com.sub9.userservice.notification.infrastructure.presentation.response.ApiError;
import com.sub9.userservice.notification.infrastructure.presentation.response.ApiErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotificationNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ApiErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(
                ApiErrorCode.MISSING_REQUIRED_VALUE,
                List.of(Map.of(exception.getHeaderName(), "This header is required."))
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ApiError.of(
                ApiErrorCode.INVALID_REQUEST,
                List.of(Map.of(exception.getName(), "The value format is invalid."))
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "The value is invalid.")
                ))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.INVALID_REQUEST, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        lastPathSegment(violation.getPropertyPath().toString()),
                        violation.getMessage()
                ))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.INVALID_REQUEST, errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ignored) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(ApiErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Unexpected notification API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ApiErrorCode.INTERNAL_SERVER_ERROR));
    }

    private String lastPathSegment(String path) {
        int separatorIndex = path.lastIndexOf('.');
        return separatorIndex < 0 ? path : path.substring(separatorIndex + 1);
    }
}