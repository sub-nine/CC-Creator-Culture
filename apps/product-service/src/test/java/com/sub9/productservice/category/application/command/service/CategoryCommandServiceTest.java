package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCreatedEventPort;
import com.sub9.productservice.category.application.command.port.out.HashtagProductCommandRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.domain.service.HashtagCategorySimilarityDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryCommandService 단위 테스트")
class CategoryCommandServiceTest {

    @Mock
    private CategoryCommandRepository categoryCommandRepository;
    @Mock
    private HashtagCommandRepository hashtagCommandRepository;
    @Mock
    private HashtagProductCommandRepository hashtagProductCommandRepository;
    @Mock
    private HashtagCreatedEventPort hashtagCreatedEventPort;
    @Mock
    private HashtagCategorySimilarityDomainService hashtagCategorySimilarityDomainService;

    private CategoryCommandService categoryCommandService;

    @BeforeEach
    void setUp() {
        categoryCommandService = new CategoryCommandService(
                categoryCommandRepository,
                hashtagCommandRepository,
                hashtagProductCommandRepository,
                hashtagCreatedEventPort,
                hashtagCategorySimilarityDomainService
        );
    }

    @Nested
    @DisplayName("tryLink()")
    class TryLink {

        @Test
        @DisplayName("존재하지 않는 해시태그면 예외를 던진다")
        void hashtagNotFound_throwsException() {
            UUID hashtagId = UUID.randomUUID();
            when(categoryCommandRepository.findAllActive()).thenReturn(List.of());
            when(hashtagCommandRepository.findById(hashtagId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryCommandService.tryLink(hashtagId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(CategoryErrorCode.HASHTAG_NOT_FOUND);

            verify(categoryCommandRepository, never()).linkCategoryHashtag(any());
        }

        @Test
        @DisplayName("유사도가 MERGE_THRESHOLD 이상인 카테고리는 MERGED/ALGORITHM으로 연결하고 usage_count를 증가시킨다")
        void similarityAboveMergeThreshold_linksAsMergedAndIncreasesUsageCount() {
            Category category = Category.create("강아지", null);
            Hashtag hashtag = Hashtag.create("강아지용품");

            when(categoryCommandRepository.findAllActive()).thenReturn(List.of(category));
            when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
            when(hashtagCategorySimilarityDomainService.calculateSimilarity(category.getName(), hashtag.getName()))
                    .thenReturn(0.85);
            when(categoryCommandRepository.findCategoryHashtagByCategoryIdAndHashtagId(category.getId(), hashtag.getId()))
                    .thenReturn(Optional.empty());

            categoryCommandService.tryLink(hashtag.getId());

            ArgumentCaptor<CategoryHashtag> captor = ArgumentCaptor.forClass(CategoryHashtag.class);
            verify(categoryCommandRepository).linkCategoryHashtag(captor.capture());
            CategoryHashtag linked = captor.getValue();
            assertThat(linked.getCategory()).isEqualTo(category);
            assertThat(linked.getHashtag()).isEqualTo(hashtag);
            assertThat(linked.getMatchType()).isEqualTo(CategoryHashtagMatchType.ALGORITHM);
            assertThat(linked.getStatus()).isEqualTo(CategoryHashtagStatus.MERGED);
            assertThat(linked.getSimilarityScore()).isEqualTo(0.85);

            verify(hashtagCommandRepository).increaseUsageCount(hashtag.getId());
            verify(categoryCommandRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("유사도가 PENDING_APPROVAL_THRESHOLD 이상 MERGE_THRESHOLD 미만이면 PENDING_APPROVAL/ALGORITHM으로 연결하고 usage_count는 증가시키지 않는다")
        void similarityBetweenThresholds_linksAsPendingApprovalWithoutIncreasingUsageCount() {
            Category category = Category.create("강아지", null);
            Hashtag hashtag = Hashtag.create("강아쥐");

            when(categoryCommandRepository.findAllActive()).thenReturn(List.of(category));
            when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
            when(hashtagCategorySimilarityDomainService.calculateSimilarity(category.getName(), hashtag.getName()))
                    .thenReturn(0.75);
            when(categoryCommandRepository.findCategoryHashtagByCategoryIdAndHashtagId(category.getId(), hashtag.getId()))
                    .thenReturn(Optional.empty());

            categoryCommandService.tryLink(hashtag.getId());

            ArgumentCaptor<CategoryHashtag> captor = ArgumentCaptor.forClass(CategoryHashtag.class);
            verify(categoryCommandRepository).linkCategoryHashtag(captor.capture());
            CategoryHashtag linked = captor.getValue();
            assertThat(linked.getMatchType()).isEqualTo(CategoryHashtagMatchType.ALGORITHM);
            assertThat(linked.getStatus()).isEqualTo(CategoryHashtagStatus.PENDING_APPROVAL);
            assertThat(linked.getSimilarityScore()).isEqualTo(0.75);

            verify(hashtagCommandRepository, never()).increaseUsageCount(any());
            verify(categoryCommandRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("이미 연결된 카테고리-해시태그 조합은 중복 연결하지 않는다")
        void alreadyLinkedCategoryHashtag_isSkipped() {
            Category category = Category.create("강아지", null);
            Hashtag hashtag = Hashtag.create("강아지용품");

            when(categoryCommandRepository.findAllActive()).thenReturn(List.of(category));
            when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
            when(hashtagCategorySimilarityDomainService.calculateSimilarity(category.getName(), hashtag.getName()))
                    .thenReturn(0.9);
            when(categoryCommandRepository.findCategoryHashtagByCategoryIdAndHashtagId(category.getId(), hashtag.getId()))
                    .thenReturn(Optional.of(CategoryHashtag.create(
                            category, hashtag, CategoryHashtagMatchType.ALGORITHM, CategoryHashtagStatus.MERGED, 0.9)));

            categoryCommandService.tryLink(hashtag.getId());

            verify(categoryCommandRepository, never()).linkCategoryHashtag(any());
            verify(hashtagCommandRepository, never()).increaseUsageCount(any());
            verify(categoryCommandRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("활성 카테고리가 하나도 없으면 해시태그 이름으로 새 카테고리를 만들어 PROMOTED/MERGED로 연결한다")
        void noActiveCategories_promotesHashtagToNewCategory() {
            Hashtag hashtag = Hashtag.create("신규카테고리");

            when(categoryCommandRepository.findAllActive()).thenReturn(List.of());
            when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
            when(categoryCommandRepository.save(any(Category.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            categoryCommandService.tryLink(hashtag.getId());

            ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
            verify(categoryCommandRepository).save(categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().getName()).isEqualTo(hashtag.getName());

            ArgumentCaptor<CategoryHashtag> categoryHashtagCaptor = ArgumentCaptor.forClass(CategoryHashtag.class);
            verify(categoryCommandRepository).linkCategoryHashtag(categoryHashtagCaptor.capture());
            CategoryHashtag linked = categoryHashtagCaptor.getValue();
            assertThat(linked.getCategory()).isEqualTo(categoryCaptor.getValue());
            assertThat(linked.getHashtag()).isEqualTo(hashtag);
            assertThat(linked.getMatchType()).isEqualTo(CategoryHashtagMatchType.PROMOTED);
            assertThat(linked.getStatus()).isEqualTo(CategoryHashtagStatus.MERGED);
            assertThat(linked.getSimilarityScore()).isEqualTo(0.0);

            verify(hashtagCommandRepository).increaseUsageCount(hashtag.getId());
        }

        @Test
        @DisplayName("모든 카테고리와의 유사도가 PENDING_APPROVAL_THRESHOLD 미만이면 새 카테고리를 만들어 연결한다")
        void allSimilaritiesBelowThreshold_promotesHashtagToNewCategory() {
            Category category = Category.create("전자기기", null);
            Hashtag hashtag = Hashtag.create("수제비누");

            when(categoryCommandRepository.findAllActive()).thenReturn(List.of(category));
            when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
            when(hashtagCategorySimilarityDomainService.calculateSimilarity(category.getName(), hashtag.getName()))
                    .thenReturn(0.1);
            when(categoryCommandRepository.save(any(Category.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            categoryCommandService.tryLink(hashtag.getId());

            verify(categoryCommandRepository).save(any(Category.class));

            ArgumentCaptor<CategoryHashtag> captor = ArgumentCaptor.forClass(CategoryHashtag.class);
            verify(categoryCommandRepository).linkCategoryHashtag(captor.capture());
            assertThat(captor.getValue().getMatchType()).isEqualTo(CategoryHashtagMatchType.PROMOTED);

            verify(categoryCommandRepository, never())
                    .findCategoryHashtagByCategoryIdAndHashtagId(any(), any());
        }
    }
}
