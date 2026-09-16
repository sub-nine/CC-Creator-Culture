package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.productservice.category.application.command.port.in.LinkHashtagToCategoryUseCase;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.port.out.OutboxRepository;
import com.sub9.productservice.category.application.command.similarity.CategorySimilarityPipeline;
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
    private final OutboxRepository outboxRepository;

    @Override
    @Transactional
    public void tryLink(UUID hashtagId) {
        Hashtag hashtag = hashtagCommandRepository.findById(hashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.HASHTAG_NOT_FOUND));
        List<Category> categories = categoryCommandRepository.findAllActive();

        List<CategoryCandidateResult> candidateResults = categorySimilarityPipeline.resolve(hashtag, categories);

        boolean matchedAnyCategory = false;
        boolean anyFailed = false;

        for (CategoryCandidateResult candidateResult : candidateResults) {
            CandidateOutcome outcome = linkCandidate(hashtagId, hashtag, candidateResult);
            if (outcome == CandidateOutcome.MATCHED) {
                matchedAnyCategory = true;
            } else if (outcome == CandidateOutcome.FAILED) {
                anyFailed = true;
            }
        }

        if (!matchedAnyCategory && !anyFailed) {
            // 아무 카테고리와 연결되지 않았으며, 검사 실패가 없었던 경우 (해시태그를 신규 카테고리로 승격할 수 있는 확신)
            promoteToNewCategory(hashtag, CategoryHashtagStatus.MERGED);
        } else if (!matchedAnyCategory) {
            // anyFailed가 true인 경우 - 검사 실패로 이번엔 보류, 스케줄러가 나중에 재시도
            // TODO: 최종적으로 아무런 카테고리와 연결되지 않은 해시태그는 스케줄러를 통해 재검사
            log.warn("[CATEGORY] 검사 실패로 카테고리 연결 보류 - hashtagId: {}", hashtagId);
        }
    }

    private enum CandidateOutcome {
        MATCHED, FAILED, NOT_SIMILAR
    }

    private CandidateOutcome linkCandidate(UUID hashtagId, Hashtag hashtag, CategoryCandidateResult candidateResult) {
        Category category = candidateResult.category();

        return switch (candidateResult.result()) {
            case CategoryMatchResult.Merge merge -> {
                linkIfAbsent(category, hashtag, CategoryHashtagStatus.MERGED, merge.matchType(), merge.similarity());
                yield CandidateOutcome.MATCHED;
            }
            case CategoryMatchResult.PendingApproval pendingApproval -> {
                linkIfAbsent(category, hashtag, CategoryHashtagStatus.PENDING_APPROVAL,
                        pendingApproval.matchType(), pendingApproval.similarity());
                yield CandidateOutcome.MATCHED;
            }
            case CategoryMatchResult.Failed failed -> {
                log.warn("[CATEGORY] 유사도 판단 실패 - hashtagId: {}, categoryId: {}, reason: {}",
                        hashtagId, category.getId(), failed.reason());
                yield CandidateOutcome.FAILED;
            }
            // 이 후보와는 연결하지 않음
            case CategoryMatchResult.NotSimilar notSimilar -> CandidateOutcome.NOT_SIMILAR;
        };
    }

    private void promoteToNewCategory(Hashtag hashtag, CategoryHashtagStatus status) {
        // findOrCreateByName 자체가 원자적이라, 동시에 같은 이름으로 승격을 시도해도 카테고리는 하나만 생성됨
        Category newCategory = categoryCommandRepository.findOrCreateByName(hashtag.getName());

        // TODO: findOrCreateByName이 기존 카테고리를 반환한 경우(이미 벡터 있음)에도 중복 발행됨 - created 여부 구분 필요
        outboxRepository.record(new CategoryCreatedEvent(newCategory.getId()));

        linkIfAbsent(newCategory, hashtag, status, CategoryHashtagMatchType.PROMOTED, 0.0);
    }

    private void linkIfAbsent(
            Category category,
            Hashtag hashtag,
            CategoryHashtagStatus status,
            CategoryHashtagMatchType matchType,
            double similarity
    ) {
        // 존재 확인 후 삽입(check-then-act) 대신 원자적 삽입을 써서, 동시에 같은 (category, hashtag)
        // 조합으로 링크가 시도돼도 unique 제약 위반 예외 없이 하나로 수렴함
        categoryCommandRepository.linkCategoryHashtagIfAbsent(
                CategoryHashtag.create(category, hashtag, matchType, status, similarity)
        );
    }
}
