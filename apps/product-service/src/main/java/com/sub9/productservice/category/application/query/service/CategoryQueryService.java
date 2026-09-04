package com.sub9.productservice.category.application.query.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.application.query.repository.CategoryHashtagQueryRepository;
import com.sub9.productservice.category.application.query.repository.CategoryQueryRepository;
import com.sub9.productservice.category.application.query.repository.HashtagQueryRepository;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import com.sub9.productservice.category.presentation.query.dto.CategoryDetailResponse;
import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.category.presentation.query.dto.MergeRequestResponse;
import com.sub9.productservice.leaderboard.application.port.CategoryQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryQueryService implements CategoryQueryPort {

    private final CategoryQueryRepository categoryQueryRepository;
    private final HashtagQueryRepository hashtagQueryRepository;
    private final CategoryHashtagQueryRepository categoryHashtagQueryRepository;

    public Page<CategoryResponse> searchCategories(String keyword, Pageable pageable) {
        return categoryQueryRepository.searchCategories(keyword, pageable);
    }

    public CategoryDetailResponse getCategory(UUID categoryId) {
        CategoryResponse category = categoryQueryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        List<HashtagResponse> hashtags = categoryHashtagQueryRepository.findHashtagsByCategoryId(categoryId);

        return new CategoryDetailResponse(category.categoryId(), category.name(), category.description(), hashtags);
    }

    public Page<HashtagResponse> getCategoryHashtags(UUID categoryId, Pageable pageable) {
        categoryQueryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        return categoryHashtagQueryRepository.findHashtagsByCategoryId(categoryId, pageable);
    }

    public Page<MergeRequestResponse> getMergeRequests(Pageable pageable) {
        return categoryHashtagQueryRepository.findPendingMergeRequests(pageable);
    }

    public Page<HashtagResponse> searchHashtags(String keyword, Pageable pageable) {
        return hashtagQueryRepository.searchHashtags(keyword, pageable);
    }

    @Override
    public List<CategoryResponse> getCategoriesByIds(List<UUID> ids) {
        return categoryQueryRepository.searchCategoriesByIds(ids);
    }
}
