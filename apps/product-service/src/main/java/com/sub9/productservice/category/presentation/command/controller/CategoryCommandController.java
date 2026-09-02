package com.sub9.productservice.category.presentation.command.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.category.presentation.command.dto.CreateCategoryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CategoryCommandController {

    /**
     * 카테고리 추가 (MASTER)
     */
    @PostMapping("/api/v1/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UUID> createCategory(
            @RequestBody @Valid CreateCategoryRequest request
    ) {
        // TODO: Command Service 연동
        return ApiResponse.success("카테고리 생성 성공", UUID.randomUUID());
    }

    /**
     * 카테고리-해시태그 연결 (MASTER)
     */
    @PostMapping("/api/v1/admin/categories/{categoryId}/hashtags/{hashtagId}")
    public ApiResponse<Void> linkHashtag(
            @PathVariable UUID categoryId,
            @PathVariable UUID hashtagId
    ) {
        // TODO: Command Service 연동
        return ApiResponse.success("카테고리-해시태그 연결 성공", null);
    }

    /**
     * 카테고리-해시태그 연결 해제 (MASTER, MANAGER)
     */
    @DeleteMapping("/api/v1/admin/categories/{categoryId}/hashtags/{hashtagId}")
    public ApiResponse<Void> unlinkHashtag(
            @PathVariable UUID categoryId,
            @PathVariable UUID hashtagId
    ) {
        // TODO: Command Service 연동
        return ApiResponse.success("카테고리-해시태그 연결 해제 성공", null);
    }

    /**
     * 모호한 해시태그 연결 승인 (MASTER, MANAGER)
     */
    @PostMapping("/api/v1/admin/categories/merge-requests/{requestId}/approve")
    public ApiResponse<Void> approveMergeRequest(
            @PathVariable UUID requestId
    ) {
        // TODO: Command Service 연동
        return ApiResponse.success("해시태그 연결 승인 성공", null);
    }

    /**
     * 모호한 해시태그 연결 반려 (MASTER, MANAGER)
     */
    @PostMapping("/api/v1/admin/categories/merge-requests/{requestId}/reject")
    public ApiResponse<Void> rejectMergeRequest(
            @PathVariable UUID requestId
    ) {
        // TODO: Command Service 연동
        return ApiResponse.success("해시태그 연결 반려 성공", null);
    }

    /**
     * 해시태그 삭제 (MASTER, MANAGER)
     */
    @DeleteMapping("/api/v1/hashtags/{hashtagId}")
    public ApiResponse<Void> deleteHashtag(
            @PathVariable UUID hashtagId
    ) {
        // TODO: Command Service 연동
        return ApiResponse.success("해시태그 삭제 성공", null);
    }
}
