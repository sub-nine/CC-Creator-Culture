package com.sub9.userservice.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.domain.exception.AuthenticationTokenStorageException;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import com.sub9.userservice.auth.presentation.request.LoginRequest;
import com.sub9.userservice.auth.presentation.response.LoginResponse;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 서비스")
class LoginServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Password123!";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final Duration ACCESS_EXPIRATION = Duration.ofMinutes(30);
    private static final Duration REFRESH_EXPIRATION = Duration.ofDays(7);

    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private AuthenticationTokenRepository authenticationTokenRepository;
    @Mock
    private User user;
    @Mock
    private Creator creator;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(
                userRepository,
                creatorRepository,
                passwordEncoder,
                new SignupInputNormalizer(),
                tokenProvider,
                authenticationTokenRepository);
    }

    @Test
    @DisplayName("CUSTOMER의 자격 증명을 확인하고 토큰을 발급·저장한다")
    void logs_in_customer_and_stores_refresh_token() {
        givenValidUser(UserRole.CUSTOMER);
        givenIssuedTokens();

        LoginResponse response = loginService.login(new LoginRequest(" User@Example.COM ", PASSWORD));

        verify(userRepository).findActiveByEmail(EMAIL);
        verify(tokenProvider).issueAccessToken(USER_ID, UserRole.CUSTOMER);
        verify(tokenProvider).issueRefreshToken(USER_ID, UserRole.CUSTOMER);
        verify(authenticationTokenRepository)
                .saveRefreshToken(USER_ID, REFRESH_TOKEN, REFRESH_EXPIRATION);
        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
    }

    @Test
    @DisplayName("APPROVED CREATOR에게만 CREATOR 토큰을 발급한다")
    void logs_in_approved_creator() {
        givenValidUser(UserRole.CREATOR);
        when(creatorRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.of(creator));
        when(creator.getApprovalStatus()).thenReturn(ApprovalStatus.APPROVED);
        givenIssuedTokens();

        LoginResponse response = loginService.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        verify(authenticationTokenRepository)
                .saveRefreshToken(USER_ID, REFRESH_TOKEN, REFRESH_EXPIRATION);
    }

    @Test
    @DisplayName("PENDING CREATOR는 AUTH_0003으로 차단하고 토큰을 발급하지 않는다")
    void rejects_pending_creator() {
        givenCreator(ApprovalStatus.PENDING);

        assertBusinessError(
                () -> loginService.login(new LoginRequest(EMAIL, PASSWORD)),
                AuthErrorCode.CREATOR_APPROVAL_PENDING);
        verify(tokenProvider, never()).issueAccessToken(USER_ID, UserRole.CREATOR);
        verify(authenticationTokenRepository, never())
                .saveRefreshToken(USER_ID, REFRESH_TOKEN, REFRESH_EXPIRATION);
    }

    @Test
    @DisplayName("REJECTED CREATOR는 AUTH_0004로 차단하고 토큰을 발급하지 않는다")
    void rejects_rejected_creator() {
        givenCreator(ApprovalStatus.REJECTED);

        assertBusinessError(
                () -> loginService.login(new LoginRequest(EMAIL, PASSWORD)),
                AuthErrorCode.CREATOR_APPROVAL_REJECTED);
        verify(tokenProvider, never()).issueAccessToken(USER_ID, UserRole.CREATOR);
    }

    @Test
    @DisplayName("존재하지 않는 이메일은 AUTH_0001로 처리한다")
    void rejects_unknown_email() {
        when(userRepository.findActiveByEmail(EMAIL)).thenReturn(Optional.empty());

        assertBusinessError(
                () -> loginService.login(new LoginRequest(EMAIL, PASSWORD)),
                AuthErrorCode.INVALID_CREDENTIALS);
        verify(passwordEncoder, never()).matches(PASSWORD, ENCODED_PASSWORD);
        verify(tokenProvider, never()).issueAccessToken(USER_ID, UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("비밀번호 불일치는 AUTH_0001로 처리하고 승인 상태를 조회하지 않는다")
    void rejects_wrong_password_before_checking_approval() {
        when(userRepository.findActiveByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(user.getPassword()).thenReturn(ENCODED_PASSWORD);
        when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        assertBusinessError(
                () -> loginService.login(new LoginRequest(EMAIL, PASSWORD)),
                AuthErrorCode.INVALID_CREDENTIALS);
        verify(creatorRepository, never()).findActiveByUserId(USER_ID);
        verify(tokenProvider, never()).issueAccessToken(USER_ID, UserRole.CREATOR);
    }

    @Test
    @DisplayName("CREATOR 정보가 없으면 자격 증명 오류로 로그인하지 않는다")
    void rejects_creator_without_active_creator_profile() {
        givenValidUser(UserRole.CREATOR);
        when(creatorRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.empty());

        assertBusinessError(
                () -> loginService.login(new LoginRequest(EMAIL, PASSWORD)),
                AuthErrorCode.INVALID_CREDENTIALS);
        verify(tokenProvider, never()).issueAccessToken(USER_ID, UserRole.CREATOR);
    }

    @Test
    @DisplayName("Refresh Token 저장 실패 시 503 오류를 전달하고 로그인 응답을 반환하지 않는다")
    void propagates_redis_failure_as_service_unavailable() {
        givenValidUser(UserRole.CUSTOMER);
        givenIssuedTokensWithoutResponseExpiration();
        org.mockito.Mockito.doThrow(new AuthenticationTokenStorageException())
                .when(authenticationTokenRepository)
                .saveRefreshToken(USER_ID, REFRESH_TOKEN, REFRESH_EXPIRATION);

        assertBusinessError(
                () -> loginService.login(new LoginRequest(EMAIL, PASSWORD)),
                CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    private void givenValidUser(UserRole role) {
        when(userRepository.findActiveByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(USER_ID);
        when(user.getPassword()).thenReturn(ENCODED_PASSWORD);
        when(user.getRole()).thenReturn(role);
        when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
    }

    private void givenCreator(ApprovalStatus approvalStatus) {
        givenValidUser(UserRole.CREATOR);
        when(creatorRepository.findActiveByUserId(USER_ID)).thenReturn(Optional.of(creator));
        when(creator.getApprovalStatus()).thenReturn(approvalStatus);
    }

    private void givenIssuedTokens() {
        givenIssuedTokensWithoutResponseExpiration();
        when(tokenProvider.accessTokenExpiration()).thenReturn(ACCESS_EXPIRATION);
    }

    private void givenIssuedTokensWithoutResponseExpiration() {
        when(tokenProvider.issueAccessToken(USER_ID, user.getRole())).thenReturn(ACCESS_TOKEN);
        when(tokenProvider.issueRefreshToken(USER_ID, user.getRole())).thenReturn(REFRESH_TOKEN);
        when(tokenProvider.refreshTokenExpiration()).thenReturn(REFRESH_EXPIRATION);
    }

    private void assertBusinessError(Runnable operation, com.sub9.common.exception.ErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isSameAs(errorCode));
    }
}
