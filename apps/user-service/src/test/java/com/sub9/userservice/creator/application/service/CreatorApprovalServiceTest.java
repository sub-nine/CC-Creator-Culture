package com.sub9.userservice.creator.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.presentation.request.CreatorApprovalRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("창작자 가입 심사 서비스")
class CreatorApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T02:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Mock
    private CreatorRepository creatorRepository;

    private CreatorApprovalService creatorApprovalService;

    @BeforeEach
    void setUp() {
        creatorApprovalService = new CreatorApprovalService(
                creatorRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("PENDING 창작자를 승인한다")
    void when_pending_creator_is_approved_response_contains_approval_information() {
        UUID creatorId = uuidGenerator.generate();
        UUID adminId = uuidGenerator.generate();
        Creator creator = pendingCreator(creatorId);
        when(creatorRepository.findActiveByIdForUpdate(creatorId))
                .thenReturn(Optional.of(creator));

        var response = creatorApprovalService.review(
                creatorId, adminId, new CreatorApprovalRequest(ApprovalStatus.APPROVED));

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.approvedBy()).isEqualTo(adminId);
        assertThat(response.approvedAt())
                .isEqualTo(LocalDateTime.parse("2026-09-10T02:00:00"));
    }

    @Test
    @DisplayName("PENDING 창작자를 거절한다")
    void when_pending_creator_is_rejected_response_has_no_approval_information() {
        UUID creatorId = uuidGenerator.generate();
        UUID adminId = uuidGenerator.generate();
        Creator creator = pendingCreator(creatorId);
        when(creatorRepository.findActiveByIdForUpdate(creatorId))
                .thenReturn(Optional.of(creator));

        var response = creatorApprovalService.review(
                creatorId, adminId, new CreatorApprovalRequest(ApprovalStatus.REJECTED));

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(response.approvedBy()).isNull();
        assertThat(response.approvedAt()).isNull();
        assertThat(creator.getUpdatedBy()).isEqualTo(adminId);
    }

    @Test
    @DisplayName("존재하지 않는 창작자는 404 오류로 처리한다")
    void when_creator_does_not_exist_not_found_error_is_returned() {
        UUID creatorId = uuidGenerator.generate();
        when(creatorRepository.findActiveByIdForUpdate(creatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorApprovalService.review(
                creatorId,
                uuidGenerator.generate(),
                new CreatorApprovalRequest(ApprovalStatus.APPROVED)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_NOT_FOUND));
    }

    @Test
    @DisplayName("이미 심사된 창작자는 409 오류로 처리한다")
    void when_creator_is_already_reviewed_conflict_error_is_returned() {
        UUID creatorId = uuidGenerator.generate();
        Creator creator = pendingCreator(creatorId);
        creator.approve(uuidGenerator.generate(), NOW.minusSeconds(60));
        when(creatorRepository.findActiveByIdForUpdate(creatorId))
                .thenReturn(Optional.of(creator));

        assertThatThrownBy(() -> creatorApprovalService.review(
                creatorId,
                uuidGenerator.generate(),
                new CreatorApprovalRequest(ApprovalStatus.REJECTED)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_ALREADY_REVIEWED));
    }

    private Creator pendingCreator(UUID creatorId) {
        return Creator.createPending(
                creatorId,
                uuidGenerator.generate(),
                "창작상점",
                "123-45-67890",
                Instant.parse("2026-09-01T02:00:00Z"));
    }
}
