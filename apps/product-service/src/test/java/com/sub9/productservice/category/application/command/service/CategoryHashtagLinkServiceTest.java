package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryHashtagLinkService 단위 테스트")
class CategoryHashtagLinkServiceTest {

    @Mock
    private CategoryCommandRepository categoryCommandRepository;
    @Mock
    private HashtagCommandRepository hashtagCommandRepository;
    @Mock
    private CategorySimilarityPipeline categorySimilarityPipeline;

    private CategoryHashtagLinkService categoryHashtagLinkService;

    @BeforeEach
    void setUp() {
        categoryHashtagLinkService = new CategoryHashtagLinkService(
                categoryCommandRepository, hashtagCommandRepository, categorySimilarityPipeline);
    }

    @Test
    @DisplayName("존재하지 않는 해시태그면 예외를 던지고 파이프라인을 호출하지 않는다")
    void tryLink_hashtagNotFound_throwsException() {
        UUID hashtagId = UUID.randomUUID();
        when(hashtagCommandRepository.findById(hashtagId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryHashtagLinkService.tryLink(hashtagId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CategoryErrorCode.HASHTAG_NOT_FOUND);

        verify(categorySimilarityPipeline, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("Merge와 PendingApproval이 서로 다른 카테고리에 동시에 나오면 둘 다 연결한다 (다대다 연결)")
    void tryLink_mergeAndPendingApprovalForDifferentCategories_linksBoth() {
        Category mergeCategory = Category.create("액체괴물", null);
        Category pendingCategory = Category.create("말랑이", null);
        Hashtag hashtag = Hashtag.create("슬라임");

        when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
        when(categoryCommandRepository.findAllActive()).thenReturn(List.of(mergeCategory, pendingCategory));
        when(categorySimilarityPipeline.resolve(hashtag, List.of(mergeCategory, pendingCategory))).thenReturn(List.of(
                new CategoryCandidateResult(mergeCategory, new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, 0.9)),
                new CategoryCandidateResult(pendingCategory, new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.ALGORITHM, 0.72))
        ));
        when(categoryCommandRepository.findCategoryHashtagByCategoryIdAndHashtagId(any(), any()))
                .thenReturn(Optional.empty());

        categoryHashtagLinkService.tryLink(hashtag.getId());

        ArgumentCaptor<CategoryHashtag> captor = ArgumentCaptor.forClass(CategoryHashtag.class);
        verify(categoryCommandRepository, times(2)).linkCategoryHashtag(captor.capture());

        CategoryHashtag mergedLink = captor.getAllValues().stream()
                .filter(link -> link.getCategory().equals(mergeCategory)).findFirst().orElseThrow();
        assertThat(mergedLink.getStatus()).isEqualTo(CategoryHashtagStatus.MERGED);

        CategoryHashtag pendingLink = captor.getAllValues().stream()
                .filter(link -> link.getCategory().equals(pendingCategory)).findFirst().orElseThrow();
        assertThat(pendingLink.getStatus()).isEqualTo(CategoryHashtagStatus.PENDING_APPROVAL);

        verify(categoryCommandRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("이미 연결된 카테고리-해시태그 조합은 Merge/PendingApproval이어도 중복 연결하지 않는다")
    void tryLink_alreadyLinked_isSkipped() {
        Category category = Category.create("강아지", null);
        Hashtag hashtag = Hashtag.create("강아지용품");
        when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
        when(categoryCommandRepository.findAllActive()).thenReturn(List.of(category));
        when(categorySimilarityPipeline.resolve(hashtag, List.of(category))).thenReturn(List.of(
                new CategoryCandidateResult(category, new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, 0.9))
        ));
        when(categoryCommandRepository.findCategoryHashtagByCategoryIdAndHashtagId(category.getId(), hashtag.getId()))
                .thenReturn(Optional.of(CategoryHashtag.create(
                        category, hashtag, CategoryHashtagMatchType.ALGORITHM, CategoryHashtagStatus.MERGED, 0.9)));

        categoryHashtagLinkService.tryLink(hashtag.getId());

        verify(categoryCommandRepository, never()).linkCategoryHashtag(any());
        verify(categoryCommandRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("모든 후보가 NotSimilar면(=매칭된 카테고리 없음) 해시태그 이름으로 새 카테고리를 만들어 PROMOTED/MERGED로 연결한다")
    void tryLink_noCandidateMatched_promotesToNewCategory() {
        Category category = Category.create("전자기기", null);
        Hashtag hashtag = Hashtag.create("신규카테고리");
        when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
        when(categoryCommandRepository.findAllActive()).thenReturn(List.of(category));
        when(categorySimilarityPipeline.resolve(hashtag, List.of(category))).thenReturn(List.of(
                new CategoryCandidateResult(category, new CategoryMatchResult.NotSimilar())
        ));
        when(categoryCommandRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        categoryHashtagLinkService.tryLink(hashtag.getId());

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryCommandRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo(hashtag.getName());

        ArgumentCaptor<CategoryHashtag> linkCaptor = ArgumentCaptor.forClass(CategoryHashtag.class);
        verify(categoryCommandRepository).linkCategoryHashtag(linkCaptor.capture());
        CategoryHashtag linked = linkCaptor.getValue();
        assertThat(linked.getCategory()).isEqualTo(categoryCaptor.getValue());
        assertThat(linked.getMatchType()).isEqualTo(CategoryHashtagMatchType.PROMOTED);
        assertThat(linked.getStatus()).isEqualTo(CategoryHashtagStatus.MERGED);
    }

    @Test
    @DisplayName("활성 카테고리가 하나도 없으면(후보 자체가 없음) 새 카테고리를 만들어 연결한다")
    void tryLink_noActiveCategories_promotesToNewCategory() {
        Hashtag hashtag = Hashtag.create("신규카테고리");
        when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
        when(categoryCommandRepository.findAllActive()).thenReturn(List.of());
        when(categorySimilarityPipeline.resolve(hashtag, List.of())).thenReturn(List.of());
        when(categoryCommandRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        categoryHashtagLinkService.tryLink(hashtag.getId());

        verify(categoryCommandRepository).save(any(Category.class));
        verify(categoryCommandRepository).linkCategoryHashtag(any());
    }

    @Test
    @DisplayName("특정 후보에 대한 판단이 Failed면, 새 카테고리를 만들지 않고 그 기존 후보에 PENDING_APPROVAL로 연결한다")
    void tryLink_candidateFailed_linksExistingCandidateAsPendingApproval() {
        Category category = Category.create("애매한카테고리", null);
        Hashtag hashtag = Hashtag.create("판단불가");
        when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
        when(categoryCommandRepository.findAllActive()).thenReturn(List.of(category));
        when(categorySimilarityPipeline.resolve(hashtag, List.of(category))).thenReturn(List.of(
                new CategoryCandidateResult(category, new CategoryMatchResult.Failed("임베딩 모델 타임아웃"))
        ));
        when(categoryCommandRepository.findCategoryHashtagByCategoryIdAndHashtagId(category.getId(), hashtag.getId()))
                .thenReturn(Optional.empty());

        categoryHashtagLinkService.tryLink(hashtag.getId());

        ArgumentCaptor<CategoryHashtag> captor = ArgumentCaptor.forClass(CategoryHashtag.class);
        verify(categoryCommandRepository).linkCategoryHashtag(captor.capture());
        CategoryHashtag linked = captor.getValue();
        assertThat(linked.getCategory()).isEqualTo(category);
        assertThat(linked.getStatus()).isEqualTo(CategoryHashtagStatus.PENDING_APPROVAL);

        verify(categoryCommandRepository, never()).save(any(Category.class));
    }
}
