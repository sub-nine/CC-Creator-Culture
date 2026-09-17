package com.sub9.productservice.category.infrastructure.scheduler;

import com.sub9.productservice.category.application.command.port.in.LinkHashtagToCategoryUseCase;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// 카테고리 검사 실패(예: 임베딩 서버 타임아웃, 카테고리 벡터 미존재)로 어떤 카테고리와도
// 연결되지 못한 채 보류된 해시태그를 주기적으로 재시도
@Slf4j
@Component
@RequiredArgsConstructor
public class HashtagLinkRetryScheduler {

    private static final int BATCH_SIZE = 20;

    private final HashtagCommandRepository hashtagCommandRepository;
    private final LinkHashtagToCategoryUseCase linkHashtagToCategoryUseCase;

    // TODO: 실행 주기는 운영 중 부하를 보고 조정
    @Scheduled(fixedDelay = 60000)
    public void retryUnlinkedHashtags() {
        List<UUID> hashtagIds = hashtagCommandRepository.findIdsWithoutCategoryLink(BATCH_SIZE);

        for (UUID hashtagId : hashtagIds) {
            try {
                linkHashtagToCategoryUseCase.tryLink(hashtagId);
            } catch (Exception e) {
                // 이번 주기에 실패해도 다음 해시태그는 계속 시도하고, 이 해시태그는 다음 스케줄 주기에 다시 시도됨
                log.warn("[CATEGORY] 해시태그 재연결 재시도 실패 - hashtagId: {}", hashtagId, e);
            }
        }
    }
}
