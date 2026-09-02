package com.sub9.productservice.category.domain.exception;

import com.sub9.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {
    CATEGORY_NOT_FOUND("CATEGORY_0001", HttpStatus.NOT_FOUND, "존재하지 않는 카테고리입니다."),
    HASHTAG_NOT_FOUND("CATEGORY_0002", HttpStatus.NOT_FOUND, "존재하지 않는 해시태그입니다."),
    CATEGORY_HASHTAG_NOT_FOUND("CATEGORY_0003", HttpStatus.NOT_FOUND, "존재하지 않는 카테고리-해시태그 연결 정보입니다."),
    ALREADY_LINKED_HASHTAG("CATEGORY_0004", HttpStatus.BAD_REQUEST, "이미 카테고리에 연결된 해시태그입니다."),
    NOT_LINKED_HASHTAG("CATEGORY_0005", HttpStatus.BAD_REQUEST, "카테고리에 연결되어 있지 않은 해시태그입니다."),
    NOT_PENDING_APPROVAL("CATEGORY_0006", HttpStatus.BAD_REQUEST, "승인 대기(PENDING_APPROVAL) 상태가 아닙니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

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