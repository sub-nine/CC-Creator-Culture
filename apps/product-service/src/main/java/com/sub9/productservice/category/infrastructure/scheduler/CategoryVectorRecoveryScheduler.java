package com.sub9.productservice.category.infrastructure.scheduler;

import com.sub9.productservice.category.application.command.port.in.CalculateCategoryVectorUseCase;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// CategoryCreatedEvent 컨슈머가 재시도까지 전부 실패해 벡터가 끝내 안 생긴 카테고리를 주기적으로 재계산
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryVectorRecoveryScheduler {

    private static final int BATCH_SIZE = 20;

    private final CategoryCommandRepository categoryCommandRepository;
    private final CalculateCategoryVectorUseCase calculateCategoryVectorUseCase;

    // TODO: 실행 주기는 운영 중 부하를 보고 조정
    @Scheduled(fixedDelay = 60000)
    public void recoverMissingVectors() {
        List<UUID> categoryIds = categoryCommandRepository.findActiveIdsWithoutVector(BATCH_SIZE);

        for (UUID categoryId : categoryIds) {
            try {
                calculateCategoryVectorUseCase.calculate(categoryId);
            } catch (Exception e) {
                // 이번 주기에 실패해도 다음 카테고리는 계속 시도하고, 이 카테고리는 다음 스케줄 주기에 다시 시도됨
                log.warn("[CATEGORY] 벡터 복구 재시도 실패 - categoryId: {}", categoryId, e);
            }
        }
    }
}
