package com.sub9.productservice.category.presentation.query.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.category.application.query.service.CategoryQueryService;
import com.sub9.productservice.category.presentation.query.dto.CategoryDetailResponse;
import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.category.presentation.query.dto.MergeRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CategoryQueryController {
    private final CategoryQueryService categoryQueryService;

    /**
     * 카테고리 검색 (PUBLIC)
     */
    @GetMapping("/api/v1/categories")
    public ApiResponse<Page<CategoryResponse>> searchCategories(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(categoryQueryService.searchCategories(keyword, pageable));
    }

    /**
     * 카테고리 단건 조회 (PUBLIC)
     */
    @GetMapping("/api/v1/categories/{categoryId}")
    public ApiResponse<CategoryDetailResponse> getCategory(
            @PathVariable UUID categoryId
    ) {
        return ApiResponse.success(categoryQueryService.getCategory(categoryId));
    }

    /**
     * 카테고리 소속 해시태그 목록 조회 (PUBLIC)
     */
    @GetMapping("/api/v1/categories/{categoryId}/hashtags")
    public ApiResponse<Page<HashtagResponse>> getCategoryHashtags(
            @PathVariable UUID categoryId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(categoryQueryService.getCategoryHashtags(categoryId, pageable));
    }

    /**
     * 모호한 해시태그 연결 승인 대기 목록 조회 (MASTER, MANAGER)
     */
    @PreAuthorize("hasAnyRole  ('MASTER', 'MANAGER')")
    @GetMapping("/api/v1/admin/categories/merge-requests")
    public ApiResponse<Page<MergeRequestResponse>> getMergeRequests(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ApiResponse.success(categoryQueryService.getMergeRequests(pageable));
    }

    /**
     * 해시태그 검색 (PUBLIC)
     */
    @GetMapping("/api/v1/hashtags")
    public ApiResponse<Page<HashtagResponse>> searchHashtags(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(categoryQueryService.searchHashtags(keyword, pageable));
    }
}
