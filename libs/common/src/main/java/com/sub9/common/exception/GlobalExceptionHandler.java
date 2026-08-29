package com.sub9.common.exception;

import com.sub9.common.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        return failure(exception.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        return validationFailure(toValidationErrors(exception.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException exception) {
        return validationFailure(toValidationErrors(exception.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) {
            log.error("Return value validation failed", exception);
            return failure(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        List<Map<String, String>> errors = Stream.concat(
                        exception.getParameterValidationResults().stream()
                                .flatMap(result -> {
                                    if (result instanceof ParameterErrors parameterErrors) {
                                        return toValidationErrors(parameterErrors.getAllErrors()).stream();
                                    }
                                    return result.getResolvableErrors().stream()
                                            .map(error -> validationError(
                                                    resolveParameterName(result.getMethodParameter()),
                                                    error.getDefaultMessage()));
                                }),
                        exception.getCrossParameterValidationResults().stream()
                                .map(error -> validationError("request", error.getDefaultMessage())))
                .toList();
        return validationFailure(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception) {
        log.error("Unexpected constraint violation", exception);
        return failure(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return failure(CommonErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleResourceNotFound(Exception exception) {
        return failure(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception) {
        return failure(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception) {
        return failure(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected exception", exception);
        return failure(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> validationFailure(List<Map<String, String>> errors) {
        ErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.from(errorCode, errors));
    }

    private ResponseEntity<ErrorResponse> failure(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.from(errorCode));
    }

    private List<Map<String, String>> toValidationErrors(List<ObjectError> objectErrors) {
        return objectErrors.stream()
                .map(error -> validationError(resolveFieldName(error), error.getDefaultMessage()))
                .toList();
    }

    private Map<String, String> validationError(String field, String message) {
        String resolvedField = StringUtils.hasText(field) ? field : "request";
        String resolvedMessage = StringUtils.hasText(message)
                ? message
                : CommonErrorCode.VALIDATION_ERROR.message();
        return Map.of(resolvedField, resolvedMessage);
    }

    private String resolveFieldName(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField();
        }
        return error.getObjectName();
    }

    private String resolveParameterName(MethodParameter methodParameter) {
        RequestParam requestParam = methodParameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String name = firstNonBlank(requestParam.name(), requestParam.value());
            if (name != null) {
                return name;
            }
        }

        RequestHeader requestHeader = methodParameter.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            String name = firstNonBlank(requestHeader.name(), requestHeader.value());
            if (name != null) {
                return name;
            }
        }

        PathVariable pathVariable = methodParameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String name = firstNonBlank(pathVariable.name(), pathVariable.value());
            if (name != null) {
                return name;
            }
        }

        if (StringUtils.hasText(methodParameter.getParameterName())) {
            return methodParameter.getParameterName();
        }
        return "arg" + methodParameter.getParameterIndex();
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }
}
