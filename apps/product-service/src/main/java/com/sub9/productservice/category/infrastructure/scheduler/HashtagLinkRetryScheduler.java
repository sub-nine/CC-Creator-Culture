package com.sub9.productservice.category.infrastructure.scheduler;

import com.sub9.productservice.category.application.command.port.in.RetryUnlinkedHashtagsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HashtagLinkRetryScheduler {

    private final RetryUnlinkedHashtagsUseCase retryUnlinkedHashtagsUseCase;

    // TODO: 실행 주기는 운영 중 부하를 보고 조정
    @Scheduled(fixedDelay = 10000)
    public void execute() {
        int hashtagCount = retryUnlinkedHashtagsUseCase.retryUnlinkedHashtags();
        if (hashtagCount > 0) {
            log.info("[CATEGORY] 해시태그 재연결 재시도 요청. 해시태그 수 = {}개", hashtagCount);
        }
    }
}
