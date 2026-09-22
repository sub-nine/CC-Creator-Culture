package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.port.out.OutboxRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import com.sub9.productservice.category.presentation.command.dto.CreateCategoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCategoryCommandService {

    private final CategoryCommandRepository categoryCommandRepository;
    private final HashtagCommandRepository hashtagCommandRepository;
    private final OutboxRepository outboxRepository;

    /**
     * 카테고리 추가
     */
    @Transactional
    public UUID createCategory(CreateCategoryRequest request) {
        Category category = Category.create(request.name(), request.description());
        Category savedCategory = categoryCommandRepository.save(category);

        outboxRepository.record(new CategoryCreatedEvent(savedCategory.getId()));
        return savedCategory.getId();
    }

    /**
     * 카테고리-해시태그 수동 연결. 자동 파이프라인이 같은 조합을 먼저 연결해놨을 수 있어(레이스),
     * 이미 있으면 예외 없이 조용히 스킵(멱등)
     */
    @Transactional
    public void linkHashtag(UUID categoryId, UUID hashtagId) {
        Category category = categoryCommandRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        Hashtag hashtag = hashtagCommandRepository.findById(hashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.HASHTAG_NOT_FOUND));

        categoryCommandRepository.linkCategoryHashtagIfAbsent(
                category.linkManually(hashtag, 0.0)
        );
    }

    /**
     * 카테고리-해시태그 연결 해제
     */
    @Transactional
    public void unlinkHashtag(UUID categoryId, UUID hashtagId, UUID userId) {
        CategoryHashtag categoryHashtag = categoryCommandRepository.findCategoryHashtagByCategoryIdAndHashtagId(categoryId, hashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_HASHTAG_NOT_FOUND));

        categoryHashtag.delete(userId);
    }

    /**
     * 모호한 해시태그 연결 승인 (PENDING_APPROVAL -> MERGED)
     */
    @Transactional
    public void approveMergeRequest(UUID categoryHashtagId) {
        CategoryHashtag categoryHashtag = categoryCommandRepository.findCategoryHashtagById(categoryHashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_HASHTAG_NOT_FOUND));

        categoryHashtag.approve();
    }

    /**
     * 모호한 해시태그 연결 반려 (PENDING_APPROVAL -> REJECTED)
     */
    @Transactional
    public void rejectMergeRequest(UUID categoryHashtagId) {
        CategoryHashtag categoryHashtag = categoryCommandRepository.findCategoryHashtagById(categoryHashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_HASHTAG_NOT_FOUND));

        categoryHashtag.reject();
    }

    /**
     * 해시태그 삭제
     */
    @Transactional
    public void deleteHashtag(UUID hashtagId, UUID userId) {
        Hashtag hashtag = hashtagCommandRepository.findById(hashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.HASHTAG_NOT_FOUND));

        hashtag.delete(userId);
    }
}
