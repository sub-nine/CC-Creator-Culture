package com.sub9.productservice.category.infrastructure.scheduler;

import com.sub9.productservice.category.application.command.port.in.CalculateCategoryVectorUseCase;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryVectorRecoveryScheduler 단위 테스트")
class CategoryVectorRecoverySchedulerTest {

    @Mock
    private CategoryCommandRepository categoryCommandRepository;
    @Mock
    private CalculateCategoryVectorUseCase calculateCategoryVectorUseCase;

    private CategoryVectorRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CategoryVectorRecoveryScheduler(categoryCommandRepository, calculateCategoryVectorUseCase);
    }

    @Test
    @DisplayName("벡터 없는 카테고리 각각에 대해 벡터 계산을 재시도한다")
    void recoverMissingVectors_callsCalculateForEachCategory() {
        UUID categoryId1 = UUID.randomUUID();
        UUID categoryId2 = UUID.randomUUID();
        when(categoryCommandRepository.findActiveIdsWithoutVector(20)).thenReturn(List.of(categoryId1, categoryId2));

        scheduler.recoverMissingVectors();

        verify(calculateCategoryVectorUseCase).calculate(categoryId1);
        verify(calculateCategoryVectorUseCase).calculate(categoryId2);
    }

    @Test
    @DisplayName("한 카테고리의 재계산이 실패해도 나머지 카테고리는 계속 시도한다")
    void recoverMissingVectors_oneFails_continuesWithRest() {
        UUID failing = UUID.randomUUID();
        UUID succeeding = UUID.randomUUID();
        when(categoryCommandRepository.findActiveIdsWithoutVector(20)).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("임베딩 서버 오류")).when(calculateCategoryVectorUseCase).calculate(failing);

        scheduler.recoverMissingVectors();

        verify(calculateCategoryVectorUseCase).calculate(succeeding);
    }
}
