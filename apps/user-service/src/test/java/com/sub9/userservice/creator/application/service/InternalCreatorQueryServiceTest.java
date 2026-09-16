package com.sub9.userservice.creator.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("내부 창작자 상호명 조회 서비스")
class InternalCreatorQueryServiceTest {

    private final CreatorRepository creatorRepository = mock(CreatorRepository.class);
    private final InternalCreatorQueryService service =
            new InternalCreatorQueryService(creatorRepository);
    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("빈 ID 목록이면 저장소를 조회하지 않고 빈 목록을 반환한다")
    void when_ids_are_empty_empty_result_is_returned_without_repository_call() {
        assertThat(service.getCreatorNames(List.of())).isEmpty();
        verifyNoInteractions(creatorRepository);
    }

    @Test
    @DisplayName("조회된 창작자의 사용자 ID와 상호명을 반환한다")
    void when_active_creators_are_found_user_ids_and_names_are_returned() {
        UUID userId = uuidGenerator.generate();
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), userId, "창작상점", "1234567890", Instant.now());
        when(creatorRepository.findApprovedActiveByUserIds(List.of(userId)))
                .thenReturn(List.of(creator));

        var result = service.getCreatorNames(List.of(userId));

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.creatorId()).isEqualTo(userId);
            assertThat(response.creatorName()).isEqualTo("창작상점");
        });
    }
}
