package com.sub9.productservice.category.application.command.service;

import com.sub9.common.kafka.event.CategoryCreatedEvent;
import com.sub9.productservice.category.application.command.model.CategoryUpsertResult;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.OutboxRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryHashtagLinkWriter 단위 테스트")
class CategoryHashtagLinkWriterTest {

    @Mock
    private CategoryCommandRepository categoryCommandRepository;
    @Mock
    private OutboxRepository outboxRepository;

    private CategoryHashtagLinkWriter categoryHashtagLinkWriter;

    @BeforeEach
    void setUp() {
        categoryHashtagLinkWriter = new CategoryHashtagLinkWriter(categoryCommandRepository, outboxRepository);
    }

    @Test
    @DisplayName("Merge와 PendingApproval이 서로 다른 카테고리에 동시에 나오면 둘 다 연결한다 (다대다 연결)")
    void applyResults_mergeAndPendingApprovalForDifferentCategories_linksBoth() {
        Category mergeCategory = Category.create("액체괴물", null);
        Category pendingCategory = Category.create("말랑이", null);
        Hashtag hashtag = Hashtag.create("슬라임");

        categoryHashtagLinkWriter.applyResults(hashtag.getId(), hashtag, List.of(
                new CategoryCandidateResult(mergeCategory, new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, 0.9)),
                new CategoryCandidateResult(pendingCategory, new CategoryMatchResult.PendingApproval(CategoryHashtagMatchType.ALGORITHM, 0.72))
        ));

        ArgumentCaptor<CategoryHashtag> captor = ArgumentCaptor.forClass(CategoryHashtag.class);
        verify(categoryCommandRepository, times(2)).linkCategoryHashtagIfAbsent(captor.capture());

        CategoryHashtag mergedLink = captor.getAllValues().stream()
                .filter(link -> link.getCategory().equals(mergeCategory)).findFirst().orElseThrow();
        assertThat(mergedLink.getStatus()).isEqualTo(CategoryHashtagStatus.MERGED);

        CategoryHashtag pendingLink = captor.getAllValues().stream()
                .filter(link -> link.getCategory().equals(pendingCategory)).findFirst().orElseThrow();
        assertThat(pendingLink.getStatus()).isEqualTo(CategoryHashtagStatus.PENDING_APPROVAL);

        verify(categoryCommandRepository, never()).findOrCreateByName(any());
    }

    @Test
    @DisplayName("모든 후보가 NotSimilar면(=매칭도 실패도 없음) 해시태그 이름으로 새 카테고리를 만들어 PROMOTED/MERGED로 연결한다")
    void applyResults_allNotSimilar_promotesToNewCategory() {
        Category category = Category.create("전자기기", null);
        Hashtag hashtag = Hashtag.create("신규카테고리");
        Category newCategory = Category.create(hashtag.getName(), null);
        when(categoryCommandRepository.findOrCreateByName(hashtag.getName()))
                .thenReturn(new CategoryUpsertResult(newCategory, true));

        categoryHashtagLinkWriter.applyResults(hashtag.getId(), hashtag, List.of(
                new CategoryCandidateResult(category, new CategoryMatchResult.NotSimilar())
        ));

        verify(categoryCommandRepository).findOrCreateByName(hashtag.getName());
        verify(outboxRepository).record(new CategoryCreatedEvent(newCategory.getId()));

        ArgumentCaptor<CategoryHashtag> linkCaptor = ArgumentCaptor.forClass(CategoryHashtag.class);
        verify(categoryCommandRepository).linkCategoryHashtagIfAbsent(linkCaptor.capture());
        CategoryHashtag linked = linkCaptor.getValue();
        assertThat(linked.getCategory()).isEqualTo(newCategory);
        assertThat(linked.getMatchType()).isEqualTo(CategoryHashtagMatchType.PROMOTED);
        assertThat(linked.getStatus()).isEqualTo(CategoryHashtagStatus.MERGED);
    }

    @Test
    @DisplayName("후보 자체가 없으면 새 카테고리를 만들어 연결한다")
    void applyResults_noCandidates_promotesToNewCategory() {
        Hashtag hashtag = Hashtag.create("신규카테고리");
        Category newCategory = Category.create(hashtag.getName(), null);
        when(categoryCommandRepository.findOrCreateByName(hashtag.getName()))
                .thenReturn(new CategoryUpsertResult(newCategory, true));

        categoryHashtagLinkWriter.applyResults(hashtag.getId(), hashtag, List.of());

        verify(categoryCommandRepository).findOrCreateByName(hashtag.getName());
        verify(categoryCommandRepository).linkCategoryHashtagIfAbsent(any());
        verify(outboxRepository).record(new CategoryCreatedEvent(newCategory.getId()));
    }

    @Test
    @DisplayName("findOrCreateByName이 이미 존재하는 카테고리를 반환하면(새로 생성된 게 아니면) CategoryCreatedEvent를 발행하지 않는다")
    void applyResults_categoryAlreadyExisted_doesNotPublishEvent() {
        Hashtag hashtag = Hashtag.create("신규카테고리");
        Category existingCategory = Category.create(hashtag.getName(), null);
        when(categoryCommandRepository.findOrCreateByName(hashtag.getName()))
                .thenReturn(new CategoryUpsertResult(existingCategory, false));

        categoryHashtagLinkWriter.applyResults(hashtag.getId(), hashtag, List.of());

        verify(categoryCommandRepository).linkCategoryHashtagIfAbsent(any());
        verify(outboxRepository, never()).record(any(CategoryCreatedEvent.class));
    }

    @Test
    @DisplayName("후보가 Failed 하나뿐이면 링크도 신규 카테고리 생성도 하지 않고 보류한다")
    void applyResults_onlyFailed_holdsBackWithoutLinkingOrPromoting() {
        Category category = Category.create("애매한카테고리", null);
        Hashtag hashtag = Hashtag.create("판단불가");

        categoryHashtagLinkWriter.applyResults(hashtag.getId(), hashtag, List.of(
                new CategoryCandidateResult(category, new CategoryMatchResult.Failed("임베딩 모델 타임아웃"))
        ));

        verify(categoryCommandRepository, never()).linkCategoryHashtagIfAbsent(any());
        verify(categoryCommandRepository, never()).findOrCreateByName(any());
    }

    @Test
    @DisplayName("Failed와 NotSimilar가 섞여 있고 Merge/PendingApproval이 없으면, Failed 하나 때문에 신규 카테고리 생성도 보류한다")
    void applyResults_failedMixedWithNotSimilar_stillHoldsBackPromotion() {
        Category failedCategory = Category.create("확인못함", null);
        Category notSimilarCategory = Category.create("전혀다름", null);
        Hashtag hashtag = Hashtag.create("판단불가");

        categoryHashtagLinkWriter.applyResults(hashtag.getId(), hashtag, List.of(
                new CategoryCandidateResult(failedCategory, new CategoryMatchResult.Failed("타임아웃")),
                new CategoryCandidateResult(notSimilarCategory, new CategoryMatchResult.NotSimilar())
        ));

        verify(categoryCommandRepository, never()).linkCategoryHashtagIfAbsent(any());
        verify(categoryCommandRepository, never()).findOrCreateByName(any());
    }

    @Test
    @DisplayName("일부 후보가 Failed여도 다른 후보가 Merge/PendingApproval이면 그 후보는 정상 연결되고, Failed 후보만 링크되지 않는다")
    void applyResults_failedAlongsideMatch_linksMatchedCandidateOnly() {
        Category mergeCategory = Category.create("강아지", null);
        Category failedCategory = Category.create("확인못함", null);
        Hashtag hashtag = Hashtag.create("강아지용품");

        categoryHashtagLinkWriter.applyResults(hashtag.getId(), hashtag, List.of(
                new CategoryCandidateResult(mergeCategory, new CategoryMatchResult.Merge(CategoryHashtagMatchType.ALGORITHM, 0.9)),
                new CategoryCandidateResult(failedCategory, new CategoryMatchResult.Failed("타임아웃"))
        ));

        ArgumentCaptor<CategoryHashtag> captor = ArgumentCaptor.forClass(CategoryHashtag.class);
        verify(categoryCommandRepository, times(1)).linkCategoryHashtagIfAbsent(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(mergeCategory);
        verify(categoryCommandRepository, never()).findOrCreateByName(any());
    }
}
