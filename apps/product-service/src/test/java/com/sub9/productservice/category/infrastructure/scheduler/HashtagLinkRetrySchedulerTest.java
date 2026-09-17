package com.sub9.productservice.category.infrastructure.scheduler;

import com.sub9.productservice.category.application.command.port.in.LinkHashtagToCategoryUseCase;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
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
@DisplayName("HashtagLinkRetryScheduler 단위 테스트")
class HashtagLinkRetrySchedulerTest {

    @Mock
    private HashtagCommandRepository hashtagCommandRepository;
    @Mock
    private LinkHashtagToCategoryUseCase linkHashtagToCategoryUseCase;

    private HashtagLinkRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HashtagLinkRetryScheduler(hashtagCommandRepository, linkHashtagToCategoryUseCase);
    }

    @Test
    @DisplayName("연결 안 된 해시태그 각각에 대해 tryLink를 재시도한다")
    void retryUnlinkedHashtags_callsTryLinkForEachHashtag() {
        UUID hashtagId1 = UUID.randomUUID();
        UUID hashtagId2 = UUID.randomUUID();
        when(hashtagCommandRepository.findIdsWithoutCategoryLink(20)).thenReturn(List.of(hashtagId1, hashtagId2));

        scheduler.retryUnlinkedHashtags();

        verify(linkHashtagToCategoryUseCase).tryLink(hashtagId1);
        verify(linkHashtagToCategoryUseCase).tryLink(hashtagId2);
    }

    @Test
    @DisplayName("한 해시태그의 재시도가 실패해도 나머지 해시태그는 계속 시도한다")
    void retryUnlinkedHashtags_oneFails_continuesWithRest() {
        UUID failing = UUID.randomUUID();
        UUID succeeding = UUID.randomUUID();
        when(hashtagCommandRepository.findIdsWithoutCategoryLink(20)).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("임베딩 서버 오류")).when(linkHashtagToCategoryUseCase).tryLink(failing);

        scheduler.retryUnlinkedHashtags();

        verify(linkHashtagToCategoryUseCase).tryLink(succeeding);
    }
}
