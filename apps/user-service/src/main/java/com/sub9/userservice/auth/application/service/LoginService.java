package com.sub9.userservice.auth.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import com.sub9.userservice.auth.presentation.request.LoginRequest;
import com.sub9.userservice.auth.presentation.response.LoginResponse;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// JWT 비밀키가 설정된 환경에서만 로그인 서비스를 Spring Bean으로 등록한다.
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
public class LoginService {

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignupInputNormalizer normalizer;
    private final TokenProvider tokenProvider;
    private final AuthenticationTokenRepository authenticationTokenRepository;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findActiveByEmail(normalizer.normalizeEmail(request.email()))
                .orElseThrow(LoginService::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }

        validateCreatorApproval(user);

        String accessToken = tokenProvider.issueAccessToken(user.getId(), user.getRole());
        String refreshToken = tokenProvider.issueRefreshToken(user.getId(), user.getRole());
        authenticationTokenRepository.saveRefreshToken(
                user.getId(), refreshToken, tokenProvider.refreshTokenExpiration());

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                tokenProvider.accessTokenExpiration().toSeconds());
    }

    private void validateCreatorApproval(User user) {
        if (user.getRole() != UserRole.CREATOR) {
            return;
        }

        Creator creator = creatorRepository.findActiveByUserId(user.getId())
                .orElseThrow(LoginService::invalidCredentials);
        ApprovalStatus approvalStatus = creator.getApprovalStatus();
        switch (approvalStatus) {
            case APPROVED -> {
                return;
            }
            case PENDING -> throw new BusinessException(AuthErrorCode.CREATOR_APPROVAL_PENDING);
            case REJECTED -> throw new BusinessException(AuthErrorCode.CREATOR_APPROVAL_REJECTED);
        }
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
