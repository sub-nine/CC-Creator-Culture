package com.sub9.productservice.category.infrastructure.persistence.command.entity;

// PENDING(대기) -> PROCESSING(릴레이가 claim) -> PUBLISHED(발행 성공)
//                                        └-> FAILED(재시도 한도 초과, 데드레터)
public enum OutboxStatus {
    PENDING, PROCESSING, PUBLISHED, FAILED
}
