package com.sub9.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    String code();

    HttpStatus status();

    String message();
}
