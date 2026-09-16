package com.sub9.productservice.category.application.command.service;

import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCreatedEventPort;
import com.sub9.productservice.category.application.command.port.out.HashtagProductCommandRepository;
import com.sub9.productservice.category.application.command.model.HashtagUpsertResult;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.event.HashtagCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryCommandService 단위 테스트")
class CategoryCommandServiceTest {

    @Mock
    private HashtagCommandRepository hashtagCommandRepository;
    @Mock
    private HashtagProductCommandRepository hashtagProductCommandRepository;
    @Mock
    private HashtagCreatedEventPort hashtagCreatedEventPort;

    private CategoryCommandService categoryCommandService;

    @BeforeEach
    void setUp() {
        categoryCommandService = new CategoryCommandService(
                hashtagCommandRepository,
                hashtagProductCommandRepository,
                hashtagCreatedEventPort
        );
    }

    @Nested
    @DisplayName("addHashtagsToProduct()")
    class AddHashtagsToProduct {

        @Test
        @DisplayName("해시태그 문자열을 정규화(트림/대문자/중복제거/공백제거)해서 upsert한다")
        void normalizesHashtagStringsBeforeUpsert() {
            UUID productId = UUID.randomUUID();
            List<String> rawHashtagStrings = Arrays.asList(" cat ", "CAT", null, "", "  ", "dog");

            when(hashtagCommandRepository.findOrCreateByName(anyString()))
                    .thenAnswer(invocation -> new HashtagUpsertResult(Hashtag.create(invocation.getArgument(0)), false));

            categoryCommandService.addHashtagsToProduct(productId, rawHashtagStrings);

            verify(hashtagCommandRepository, times(1)).findOrCreateByName("CAT");
            verify(hashtagCommandRepository, times(1)).findOrCreateByName("DOG");
            verify(hashtagCommandRepository, times(2)).findOrCreateByName(anyString());
        }

        @Test
        @DisplayName("실제로 새로 연결된 해시태그만 usage_count를 증가시키고, 새로 생성된 해시태그만 이벤트를 기록한다")
        void increasesUsageCountAndRecordsEventOnlyForNewLinksAndNewHashtags() {
            UUID productId = UUID.randomUUID();
            Hashtag existingHashtag = Hashtag.create("EXISTING");
            Hashtag newHashtag = Hashtag.create("NEW");

            when(hashtagCommandRepository.findOrCreateByName("EXISTING"))
                    .thenReturn(new HashtagUpsertResult(existingHashtag, false));
            when(hashtagCommandRepository.findOrCreateByName("NEW"))
                    .thenReturn(new HashtagUpsertResult(newHashtag, true));

            when(hashtagProductCommandRepository.linkIfAbsent(existingHashtag.getId(), productId)).thenReturn(false);
            when(hashtagProductCommandRepository.linkIfAbsent(newHashtag.getId(), productId)).thenReturn(true);

            categoryCommandService.addHashtagsToProduct(productId, List.of("existing", "new"));

            verify(hashtagCommandRepository).increaseUsageCount(newHashtag.getId());
            verify(hashtagCommandRepository, never()).increaseUsageCount(existingHashtag.getId());

            ArgumentCaptor<HashtagCreatedEvent> eventCaptor = ArgumentCaptor.forClass(HashtagCreatedEvent.class);
            verify(hashtagCreatedEventPort).record(eventCaptor.capture());
            assertThat(eventCaptor.getValue().hashtagId()).isEqualTo(newHashtag.getId());
        }
    }
}
