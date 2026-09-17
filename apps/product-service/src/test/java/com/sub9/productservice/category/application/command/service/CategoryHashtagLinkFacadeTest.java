package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.similarity.CategorySimilarityPipeline;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryMatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryHashtagLinkFacade 단위 테스트")
class CategoryHashtagLinkFacadeTest {

    @Mock
    private CategoryCommandRepository categoryCommandRepository;
    @Mock
    private HashtagCommandRepository hashtagCommandRepository;
    @Mock
    private CategorySimilarityPipeline categorySimilarityPipeline;
    @Mock
    private CategoryHashtagLinkWriter categoryHashtagLinkWriter;

    private CategoryHashtagLinkFacade categoryHashtagLinkFacade;

    @BeforeEach
    void setUp() {
        categoryHashtagLinkFacade = new CategoryHashtagLinkFacade(
                categoryCommandRepository, hashtagCommandRepository, categorySimilarityPipeline, categoryHashtagLinkWriter);
    }

    @Test
    @DisplayName("존재하지 않는 해시태그면 예외를 던지고 파이프라인/writer를 호출하지 않는다")
    void tryLink_hashtagNotFound_throwsException() {
        UUID hashtagId = UUID.randomUUID();
        when(hashtagCommandRepository.findById(hashtagId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryHashtagLinkFacade.tryLink(hashtagId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CategoryErrorCode.HASHTAG_NOT_FOUND);

        verify(categorySimilarityPipeline, never()).resolve(any(), any());
        verify(categoryHashtagLinkWriter, never()).applyResults(any(), any(), any());
    }

    @Test
    @DisplayName("해시태그를 찾으면 파이프라인을 돌리고 그 결과를 writer에게 그대로 위임한다")
    void tryLink_hashtagFound_resolvesAndDelegatesToWriter() {
        Category category = Category.create("액체괴물", null);
        Hashtag hashtag = Hashtag.create("슬라임");
        List<CategoryCandidateResult> candidateResults = List.of(
                new CategoryCandidateResult(category, new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, 0.9))
        );

        when(hashtagCommandRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
        when(categoryCommandRepository.findAllActive()).thenReturn(List.of(category));
        when(categorySimilarityPipeline.resolve(hashtag, List.of(category))).thenReturn(candidateResults);

        categoryHashtagLinkFacade.tryLink(hashtag.getId());

        verify(categoryHashtagLinkWriter).applyResults(hashtag.getId(), hashtag, candidateResults);
    }
}
