package com.sub9.userservice.auth.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.domain.exception.InvalidTokenException;
import com.sub9.userservice.auth.domain.model.TokenClaims;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import com.sub9.userservice.auth.presentation.request.TokenReissueRequest;
import com.sub9.userservice.auth.presentation.response.TokenReissueResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
public class TokenReissueService {

    private final TokenProvider tokenProvider;
    private final AuthenticationTokenRepository authenticationTokenRepository;

    public TokenReissueResponse reissue(TokenReissueRequest request) {
        String refreshToken = request.refreshToken();
        TokenClaims claims = validateRefreshToken(refreshToken);
        String storedRefreshToken = authenticationTokenRepository
                .findRefreshToken(claims.userId())
                .orElseThrow(TokenReissueService::invalidRefreshToken);

        if (!tokensMatch(refreshToken, storedRefreshToken)) {
            throw invalidRefreshToken();
        }

        String accessToken = tokenProvider.issueAccessToken(claims.userId(), claims.role());
        return new TokenReissueResponse(
                accessToken,
                "Bearer",
                tokenProvider.accessTokenExpiration().toSeconds());
    }

    private TokenClaims validateRefreshToken(String refreshToken) {
        try {
            return tokenProvider.validateRefreshToken(refreshToken);
        } catch (InvalidTokenException exception) {
            throw invalidRefreshToken();
        }
    }

    private boolean tokensMatch(String requestedToken, String storedToken) {
        return MessageDigest.isEqual(
                requestedToken.getBytes(StandardCharsets.UTF_8),
                storedToken.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
