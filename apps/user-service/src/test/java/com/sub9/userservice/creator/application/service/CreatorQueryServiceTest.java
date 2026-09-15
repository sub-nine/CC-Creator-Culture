package com.sub9.userservice.creator.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("창작자 조회 서비스")
class CreatorQueryServiceTest {

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Mock
    private CreatorRepository creatorRepository;

    private CreatorQueryService creatorQueryService;

    @BeforeEach
    void setUp() {
        creatorQueryService = new CreatorQueryService(creatorRepository);
    }

    @Test
    @DisplayName("상호명 검색어를 정규화하고 내림차순으로 창작자 목록을 조회한다")
    void when_creator_list_is_requested_keyword_and_sort_are_applied() {
        Creator creator = pendingCreator("트렌드샵");
        when(creatorRepository.findApprovedActiveByCreatorName(any(), any()))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(creator), pageable, 1);
                });

        var response = creatorQueryService.getCreators(
                0, 20, "  트렌드  ", "creatorName,desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(creatorRepository).findApprovedActiveByCreatorName(
                org.mockito.ArgumentMatchers.eq("트렌드"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getSort().getOrderFor("creatorName").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(response.content()).containsExactly(
                new com.sub9.userservice.creator.presentation.response.CreatorSummaryResponse(
                        creator.getId(), "트렌드샵"));
    }

    @Test
    @DisplayName("공백 검색어는 검색 조건 없이 조회한다")
    void when_keyword_is_blank_no_keyword_condition_is_used() {
        when(creatorRepository.findApprovedActiveByCreatorName(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        creatorQueryService.getCreators(0, 20, "   ", "creatorName,asc");

        verify(creatorRepository).findApprovedActiveByCreatorName(isNull(), any());
    }

    @Test
    @DisplayName("승인된 활성 창작자 단건 정보를 조회한다")
    void when_approved_active_creator_exists_summary_is_returned() {
        Creator creator = pendingCreator("창작상점");
        when(creatorRepository.findApprovedActiveById(creator.getId()))
                .thenReturn(Optional.of(creator));

        var response = creatorQueryService.getCreator(creator.getId());

        assertThat(response.creatorId()).isEqualTo(creator.getId());
        assertThat(response.creatorName()).isEqualTo("창작상점");
    }

    @Test
    @DisplayName("조회 가능한 창작자가 없으면 404 오류로 처리한다")
    void when_creator_is_not_queryable_not_found_error_is_returned() {
        UUID creatorId = uuidGenerator.generate();
        when(creatorRepository.findApprovedActiveById(creatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorQueryService.getCreator(creatorId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_NOT_FOUND));
    }

    @Test
    @DisplayName("인증 사용자에게 연결된 승인된 활성 창작자 정보를 조회한다")
    void when_approved_active_creator_is_linked_to_user_my_creator_is_returned() {
        Creator creator = pendingCreator("내창작상점");
        when(creatorRepository.findApprovedActiveByUserId(creator.getUserId()))
                .thenReturn(Optional.of(creator));

        var response = creatorQueryService.getMyCreator(creator.getUserId());

        assertThat(response.creatorId()).isEqualTo(creator.getId());
        assertThat(response.creatorName()).isEqualTo("내창작상점");
        assertThat(response.businessRegistrationNumber()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("인증 사용자에게 조회 가능한 창작자 정보가 없으면 404 오류로 처리한다")
    void when_user_has_no_queryable_creator_not_found_error_is_returned() {
        UUID userId = uuidGenerator.generate();
        when(creatorRepository.findApprovedActiveByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorQueryService.getMyCreator(userId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_NOT_FOUND));
    }

    private Creator pendingCreator(String creatorName) {
        return Creator.createPending(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                creatorName,
                "1234567890",
                Instant.parse("2026-09-01T02:00:00Z"));
    }
}
