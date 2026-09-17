package com.sub9.productservice.category.application.command.port.in;

public interface RecoverMissingCategoryVectorsUseCase {
    // 이번 배치에서 재계산을 시도한 카테고리 수를 반환
    int recoverMissingVectors();
}
