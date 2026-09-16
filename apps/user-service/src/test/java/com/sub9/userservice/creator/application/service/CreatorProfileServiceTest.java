package com.sub9.userservice.creator.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.presentation.request.UpdateCreatorRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("내 창작자 정보 수정 서비스")
class CreatorProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T02:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Mock
    private CreatorRepository creatorRepository;

    private CreatorProfileService creatorProfileService;

    @BeforeEach
    void setUp() {
        creatorProfileService = new CreatorProfileService(
                creatorRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("전달된 창작자 정보만 정규화하여 수정한다")
    void when_creator_fields_are_provided_only_provided_values_are_updated() {
        Creator creator = creator();
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setCreatorName("  변경상점  ");
        when(creatorRepository.findApprovedActiveByUserIdForUpdate(creator.getUserId()))
                .thenReturn(Optional.of(creator));

        var response = creatorProfileService.updateMyCreator(creator.getUserId(), request);

        assertThat(response.creatorName()).isEqualTo("변경상점");
        assertThat(response.businessRegistrationNumber()).isEqualTo("1234567890");
        assertThat(creator.getUpdatedBy()).isEqualTo(creator.getUserId());
        assertThat(creator.getUpdatedAt()).isEqualTo(NOW);
        verify(creatorRepository).existsByCreatorNameIncludingDeleted("변경상점");
        verify(creatorRepository, never())
                .existsByBusinessRegistrationNumberIncludingDeleted("1234567890");
        verify(creatorRepository).flush();
    }

    @Test
    @DisplayName("사업자등록번호는 하이픈을 제거하여 수정한다")
    void when_business_number_is_provided_hyphens_are_removed() {
        Creator creator = creator();
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setBusinessRegistrationNumber("987-65-43210");
        when(creatorRepository.findApprovedActiveByUserIdForUpdate(creator.getUserId()))
                .thenReturn(Optional.of(creator));

        var response = creatorProfileService.updateMyCreator(creator.getUserId(), request);

        assertThat(response.businessRegistrationNumber()).isEqualTo("9876543210");
        verify(creatorRepository)
                .existsByBusinessRegistrationNumberIncludingDeleted("9876543210");
    }

    @Test
    @DisplayName("현재와 동일한 값은 중복 검사와 감사 갱신 없이 성공한다")
    void when_values_are_same_update_is_idempotent() {
        Creator creator = creator();
        Instant previousUpdatedAt = creator.getUpdatedAt();
        UUID previousUpdatedBy = creator.getUpdatedBy();
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setCreatorName(creator.getCreatorName());
        when(creatorRepository.findApprovedActiveByUserIdForUpdate(creator.getUserId()))
                .thenReturn(Optional.of(creator));

        var response = creatorProfileService.updateMyCreator(creator.getUserId(), request);

        assertThat(response.creatorName()).isEqualTo(creator.getCreatorName());
        assertThat(creator.getUpdatedAt()).isEqualTo(previousUpdatedAt);
        assertThat(creator.getUpdatedBy()).isEqualTo(previousUpdatedBy);
        verify(creatorRepository, never())
                .existsByCreatorNameIncludingDeleted(creator.getCreatorName());
        verify(creatorRepository, never()).flush();
    }

    @Test
    @DisplayName("이미 사용 중인 상호명으로 수정할 수 없다")
    void when_creator_name_is_duplicated_conflict_is_returned() {
        Creator creator = creator();
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setCreatorName("중복상점");
        when(creatorRepository.findApprovedActiveByUserIdForUpdate(creator.getUserId()))
                .thenReturn(Optional.of(creator));
        when(creatorRepository.existsByCreatorNameIncludingDeleted("중복상점"))
                .thenReturn(true);

        assertThatThrownBy(() ->
                creatorProfileService.updateMyCreator(creator.getUserId(), request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(UserErrorCode.CREATOR_NAME_ALREADY_EXISTS));
        verify(creatorRepository, never()).flush();
    }

    @Test
    @DisplayName("이미 사용 중인 사업자등록번호로 수정할 수 없다")
    void when_business_number_is_duplicated_conflict_is_returned() {
        Creator creator = creator();
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setBusinessRegistrationNumber("9876543210");
        when(creatorRepository.findApprovedActiveByUserIdForUpdate(creator.getUserId()))
                .thenReturn(Optional.of(creator));
        when(creatorRepository.existsByBusinessRegistrationNumberIncludingDeleted("9876543210"))
                .thenReturn(true);

        assertThatThrownBy(() ->
                creatorProfileService.updateMyCreator(creator.getUserId(), request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        UserErrorCode.BUSINESS_REGISTRATION_NUMBER_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("조회 가능한 내 창작자 정보가 없으면 404 오류로 처리한다")
    void when_my_creator_is_not_queryable_not_found_is_returned() {
        UUID userId = uuidGenerator.generate();
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setCreatorName("변경상점");
        when(creatorRepository.findApprovedActiveByUserIdForUpdate(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorProfileService.updateMyCreator(userId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_NOT_FOUND));
    }

    @Test
    @DisplayName("동시 수정으로 고유 제약이 충돌하면 일반 창작자 정보 중복 오류를 반환한다")
    void when_unique_constraint_conflicts_generic_conflict_is_returned() {
        Creator creator = creator();
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setCreatorName("경쟁상점");
        when(creatorRepository.findApprovedActiveByUserIdForUpdate(creator.getUserId()))
                .thenReturn(Optional.of(creator));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(creatorRepository).flush();

        assertThatThrownBy(() ->
                creatorProfileService.updateMyCreator(creator.getUserId(), request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_PROFILE_VALUE_ALREADY_EXISTS));
    }

    private Creator creator() {
        UUID userId = uuidGenerator.generate();
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), userId, "창작상점", "1234567890",
                Instant.parse("2026-09-01T02:00:00Z"));
        creator.approve(uuidGenerator.generate(), Instant.parse("2026-09-10T02:00:00Z"));
        return creator;
    }
}
