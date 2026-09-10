package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.application.command.port.in.AddHashtagsToProductUseCase;
import com.sub9.productservice.category.application.command.port.in.LinkHashtagToCategoryUseCase;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCreatedEventPort;
import com.sub9.productservice.category.application.command.port.out.HashtagProductCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagUpsertResult;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.event.HashtagCreatedEvent;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.domain.model.HashtagCategorySimilarityPolicy;
import com.sub9.productservice.category.domain.service.HashtagCategorySimilarityDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryCommandService implements AddHashtagsToProductUseCase, LinkHashtagToCategoryUseCase {

    private final CategoryCommandRepository categoryCommandRepository;
    private final HashtagCommandRepository hashtagCommandRepository;
    private final HashtagProductCommandRepository hashtagProductCommandRepository;
    private final HashtagCreatedEventPort hashtagCreatedEventPort;
    private final HashtagCategorySimilarityDomainService hashtagCategorySimilarityDomainService;

    @Override
    @Transactional
    public void addHashtagsToProduct(UUID productId, List<String> hashtagStrings) {
        List<String> normalizedHashtagNames = normalizeHashtagNames(hashtagStrings);
        // TODO: Hashtag Strings 필터링 ex) 오타 또는 부적절한 문자열

        // TODO: 개별 upsert(최대 2N 왕복) 대신, 존재하는 것 일괄 조회 -> 없는 것만 배치 삽입 최적화 고려
        List<HashtagUpsertResult> upsertResults = normalizedHashtagNames.stream()
                .map(hashtagCommandRepository::findOrCreateByName)
                .toList();

        List<Hashtag> hashtags = upsertResults.stream().map(HashtagUpsertResult::hashtag).toList();

        // Hashtag마다 상품과의 링크를 upsert하고, 실제로 새로 링크된 경우에만 usage_count 증가
        hashtags.stream()
                .filter(hashtag -> hashtagProductCommandRepository.linkIfAbsent(hashtag.getId(), productId))
                .forEach(hashtag -> hashtagCommandRepository.increaseUsageCount(hashtag.getId()));

        // 해시태그가 처음 생성된 경우에만 이벤트 기록
        upsertResults.stream()
                .filter(HashtagUpsertResult::created)
                .map(HashtagUpsertResult::hashtag)
                .forEach(hashtag -> hashtagCreatedEventPort.record(new HashtagCreatedEvent(hashtag.getId())));
    }

    private List<String> normalizeHashtagNames(List<String> hashtagStrings) {
        return hashtagStrings.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    @Override
    @Transactional
    public void tryLink(UUID hashtagId) {
        // TODO: MVP 개발 기한 마감에 따라 우선 알고리즘 방식의 유사도 비교만 구현, 차후 AI(임베딩 모델) 방식 도입 예정

        List<Category> categories = categoryCommandRepository.findAllActive();

        Hashtag hashtag = hashtagCommandRepository.findById(hashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.HASHTAG_NOT_FOUND));

        boolean matchedAnyCategory = false;

        for (Category category : categories) {
            double similarity = hashtagCategorySimilarityDomainService.calculateSimilarity(category.getName(), hashtag.getName());

            if (similarity >= HashtagCategorySimilarityPolicy.MERGE_THRESHOLD) {
                linkIfAbsent(category, hashtag, CategoryHashtagStatus.MERGED, similarity);
                matchedAnyCategory = true;
            } else if (similarity >= HashtagCategorySimilarityPolicy.PENDING_APPROVAL_THRESHOLD) {
                linkIfAbsent(category, hashtag, CategoryHashtagStatus.PENDING_APPROVAL, similarity);
                matchedAnyCategory = true;
            }
        }

        // 유사도가 PENDING_APPROVAL_THRESHOLD 이상인 카테고리가 하나도 없으면, 해시태그 이름으로 새 카테고리를 만들어 연결
        if (!matchedAnyCategory) {
            promoteToNewCategory(hashtag);
        }
    }

    private void promoteToNewCategory(Hashtag hashtag) {
        Category newCategory = categoryCommandRepository.save(Category.create(hashtag.getName(), null));

        categoryCommandRepository.linkCategoryHashtag(
                CategoryHashtag.create(newCategory, hashtag, CategoryHashtagMatchType.PROMOTED, CategoryHashtagStatus.MERGED, 0.0)
        );

        hashtagCommandRepository.increaseUsageCount(hashtag.getId());
    }

    private void linkIfAbsent(Category category, Hashtag hashtag, CategoryHashtagStatus status, double similarity) {
        boolean alreadyLinked = categoryCommandRepository
                .findCategoryHashtagByCategoryIdAndHashtagId(category.getId(), hashtag.getId())
                .isPresent();
        if (alreadyLinked) {
            return;
        }

        categoryCommandRepository.linkCategoryHashtag(
                CategoryHashtag.create(category, hashtag, CategoryHashtagMatchType.ALGORITHM, status, similarity)
        );

        if (status == CategoryHashtagStatus.MERGED) {
            hashtagCommandRepository.increaseUsageCount(hashtag.getId());
        }
    }

}
