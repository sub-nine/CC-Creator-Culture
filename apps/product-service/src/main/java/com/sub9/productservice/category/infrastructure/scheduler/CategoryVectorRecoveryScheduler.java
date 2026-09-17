package com.sub9.productservice.category.infrastructure.scheduler;

import com.sub9.productservice.category.application.command.port.in.RecoverMissingCategoryVectorsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryVectorRecoveryScheduler {

    private final RecoverMissingCategoryVectorsUseCase recoverMissingCategoryVectorsUseCase;

    // TODO: 실행 주기는 운영 중 부하를 보고 조정
    @Scheduled(fixedDelay = 10000)
    public void execute() {
        int categoryCount = recoverMissingCategoryVectorsUseCase.recoverMissingVectors();
        if (categoryCount > 0) {
            log.info("[CATEGORY] 카테고리 벡터 복구 재시도 요청. 카테고리 수 = {}개", categoryCount);
        }
    }
}
