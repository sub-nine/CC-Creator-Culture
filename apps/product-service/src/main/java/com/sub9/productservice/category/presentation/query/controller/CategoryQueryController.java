package com.sub9.productservice.category.presentation.query.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.category.presentation.query.dto.CategoryDetailResponse;
import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.category.presentation.query.dto.MergeRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CategoryQueryController {

    /**
     * 카테고리 검색 (PUBLIC)
     */
    @GetMapping("/api/v1/categories")
    public ApiResponse<List<CategoryResponse>> searchCategories(
            @RequestParam(required = false) String keyword
    ) {
        // TODO: Query Service 연동
        return ApiResponse.success(Collections.emptyList());
    }

    /**
     * 카테고리 단건 조회 (PUBLIC)
     */
    @GetMapping("/api/v1/categories/{categoryId}")
    public ApiResponse<CategoryDetailResponse> getCategory(
            @PathVariable UUID categoryId
    ) {
        // TODO: Query Service 연동
        return ApiResponse.success(new CategoryDetailResponse(categoryId, "", "", Collections.emptyList()));
    }

    /**
     * 카테고리 소속 해시태그 목록 조회 (PUBLIC)
     */
    @GetMapping("/api/v1/categories/{categoryId}/hashtags")
    public ApiResponse<Page<HashtagResponse>> getCategoryHashtags(
            @PathVariable UUID categoryId,
            @PageableDefault Pageable pageable
    ) {
        // TODO: Query Service 연동
        return ApiResponse.success(new PageImpl<>(Collections.emptyList(), pageable, 0));
    }

    /**
     * 모호한 해시태그 연결 승인 대기 목록 조회 (MASTER, MANAGER)
     */
    @GetMapping("/api/v1/admin/categories/merge-requests")
    public ApiResponse<List<MergeRequestResponse>> getMergeRequests() {
        // TODO: Query Service 연동
        return ApiResponse.success(Collections.emptyList());
    }

    /**
     * 해시태그 검색 (PUBLIC)
     */
    @GetMapping("/api/v1/hashtags")
    public ApiResponse<List<HashtagResponse>> searchHashtags(
            @RequestParam(required = false) String keyword
    ) {
        // TODO: Query Service 연동
        return ApiResponse.success(Collections.emptyList());
    }
}
