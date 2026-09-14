package com.sub9.userservice.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import com.sub9.userservice.user.presentation.request.UpdateMyProfileRequest;
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
@DisplayName("내 정보 조회 서비스")
class UserProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T02:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Mock
    private UserRepository userRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(
                userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("활성 사용자를 조회하면 공개 가능한 내 정보를 반환한다")
    void when_active_user_exists_get_my_profile_returns_user_information() {
        UUID userId = uuidGenerator.generate();
        User user = User.create(
                userId, "user@example.com", "encoded-password", "사용자",
                "01012345678", "서울시 예시구", "U01234567", UserRole.CUSTOMER,
                Instant.parse("2026-09-14T01:00:00Z"));
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        var response = userProfileService.getMyProfile(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.nickname()).isEqualTo("사용자");
        assertThat(response.phone()).isEqualTo("01012345678");
        assertThat(response.address()).isEqualTo("서울시 예시구");
        assertThat(response.slackId()).isEqualTo("U01234567");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        verify(userRepository).findActiveById(userId);
    }

    @Test
    @DisplayName("활성 사용자가 없으면 사용자 없음 오류를 반환한다")
    void when_active_user_does_not_exist_get_my_profile_returns_not_found() {
        UUID userId = uuidGenerator.generate();
        when(userRepository.findActiveById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getMyProfile(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("전달된 정보만 수정하고 전화번호를 정규화한다")
    void when_profile_fields_are_provided_update_my_profile_changes_only_provided_fields() {
        UUID userId = uuidGenerator.generate();
        User user = createUser(userId);
        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setNickname("  변경된 사용자  ");
        request.setPhone("010-9999-8888");
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        var response = userProfileService.updateMyProfile(userId, request);

        assertThat(response.nickname()).isEqualTo("변경된 사용자");
        assertThat(response.phone()).isEqualTo("01099998888");
        assertThat(response.address()).isEqualTo("서울시 예시구");
        assertThat(response.slackId()).isEqualTo("U01234567");
        assertThat(user.getUpdatedBy()).isEqualTo(userId);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        verify(userRepository).existsByNicknameIncludingDeleted("변경된 사용자");
        verify(userRepository).existsByPhoneIncludingDeleted("01099998888");
        verify(userRepository).flush();
    }

    @Test
    @DisplayName("명시적으로 전달된 빈 Slack ID는 삭제한다")
    void when_blank_slack_id_is_provided_update_my_profile_clears_slack_id() {
        UUID userId = uuidGenerator.generate();
        User user = createUser(userId);
        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setSlackId("   ");
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        var response = userProfileService.updateMyProfile(userId, request);

        assertThat(response.slackId()).isNull();
        verify(userRepository, never()).existsByNicknameIncludingDeleted(user.getNickname());
        verify(userRepository, never()).existsByPhoneIncludingDeleted(user.getPhone());
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임으로 수정할 수 없다")
    void when_nickname_is_duplicated_update_my_profile_returns_conflict() {
        UUID userId = uuidGenerator.generate();
        User user = createUser(userId);
        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setNickname("중복 닉네임");
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameIncludingDeleted("중복 닉네임")).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.updateMyProfile(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(UserErrorCode.NICKNAME_ALREADY_EXISTS));

        verify(userRepository, never()).flush();
    }

    @Test
    @DisplayName("동시 수정으로 고유 제약이 충돌하면 일반 회원 정보 중복 오류를 반환한다")
    void when_unique_constraint_conflicts_update_my_profile_returns_conflict() {
        UUID userId = uuidGenerator.generate();
        User user = createUser(userId);
        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setPhone("010-9999-8888");
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(userRepository).flush();

        assertThatThrownBy(() -> userProfileService.updateMyProfile(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(UserErrorCode.PROFILE_VALUE_ALREADY_EXISTS));
    }

    private User createUser(UUID userId) {
        return User.create(
                userId, "user@example.com", "encoded-password", "사용자",
                "01012345678", "서울시 예시구", "U01234567", UserRole.CUSTOMER,
                Instant.parse("2026-09-14T01:00:00Z"));
    }
}
