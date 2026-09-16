package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.application.command.port.in.LinkHashtagToCategoryUseCase;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryHashtagLinkService implements LinkHashtagToCategoryUseCase {

    private final CategoryCommandRepository categoryCommandRepository;
    private final HashtagCommandRepository hashtagCommandRepository;
    private final CategorySimilarityPipeline categorySimilarityPipeline;

    @Override
    @Transactional
    public void tryLink(UUID hashtagId) {
        Hashtag hashtag = hashtagCommandRepository.findById(hashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.HASHTAG_NOT_FOUND));
        List<Category> categories = categoryCommandRepository.findAllActive();

        List<CategoryCandidateResult> candidateResults = categorySimilarityPipeline.resolve(hashtag, categories);

        boolean matchedAnyCategory = false;
        for (CategoryCandidateResult candidateResult : candidateResults) {
            Category category = candidateResult.category();

            switch (candidateResult.result()) {
                case CategoryMatchResult.Merge merge -> {
                    linkIfAbsent(category, hashtag, CategoryHashtagStatus.MERGED, merge.matchType(), merge.similarity());
                    matchedAnyCategory = true;
                }
                case CategoryMatchResult.PendingApproval pendingApproval -> {
                    linkIfAbsent(category, hashtag, CategoryHashtagStatus.PENDING_APPROVAL,
                            pendingApproval.matchType(), pendingApproval.similarity());
                    matchedAnyCategory = true;
                }
                case CategoryMatchResult.Failed failed -> {
                    // 판단 자체가 실패한 것이라 자동 확정(MERGED) 대신 승인 대기로 걸어 관리자가 검토하게 함.
                    // matchType/similarity는 Failed가 갖고 있지 않아 판단 불가를 나타내는 placeholder 값을 씀
                    log.warn("[CATEGORY] 유사도 판단 실패로 승인 대기 처리 - hashtagId: {}, categoryId: {}, reason: {}",
                            hashtagId, category.getId(), failed.reason());
                    linkIfAbsent(category, hashtag, CategoryHashtagStatus.PENDING_APPROVAL,
                            CategoryHashtagMatchType.ALGORITHM, 0.0);
                    matchedAnyCategory = true;
                }
                case CategoryMatchResult.NotSimilar notSimilar -> {
                    // 이 후보와는 연결하지 않음
                }
            }
        }

        if (!matchedAnyCategory) {
            promoteToNewCategory(hashtag, CategoryHashtagStatus.MERGED);
        }
    }

    private void promoteToNewCategory(Hashtag hashtag, CategoryHashtagStatus status) {
        Category newCategory = categoryCommandRepository.save(Category.create(hashtag.getName(), null));

        categoryCommandRepository.linkCategoryHashtag(
                CategoryHashtag.create(newCategory, hashtag, CategoryHashtagMatchType.PROMOTED, status, 0.0)
        );
    }

    private void linkIfAbsent(
            Category category,
            Hashtag hashtag,
            CategoryHashtagStatus status,
            CategoryHashtagMatchType matchType,
            double similarity
    ) {
        boolean alreadyLinked = categoryCommandRepository
                .findCategoryHashtagByCategoryIdAndHashtagId(category.getId(), hashtag.getId())
                .isPresent();
        if (alreadyLinked) {
            return;
        }

        categoryCommandRepository.linkCategoryHashtag(
                CategoryHashtag.create(category, hashtag, matchType, status, similarity)
        );
    }
}
