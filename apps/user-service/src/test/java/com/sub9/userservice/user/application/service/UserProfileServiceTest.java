package com.sub9.userservice.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("내 정보 조회 서비스")
class UserProfileServiceTest {

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Mock
    private UserRepository userRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userRepository);
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
}
