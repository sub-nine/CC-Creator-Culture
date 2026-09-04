package com.sub9.userservice.auth.application.port.output;

import com.sub9.userservice.auth.domain.model.TokenClaims;
import com.sub9.userservice.user.domain.model.UserRole;
import java.time.Duration;
import java.util.UUID;

public interface TokenProvider {

    String issueAccessToken(UUID userId, UserRole role);

    String issueRefreshToken(UUID userId, UserRole role);

    Duration accessTokenExpiration();

    Duration refreshTokenExpiration();

    Duration clockSkew();

    TokenClaims validateAccessToken(String token);

    TokenClaims validateRefreshToken(String token);
}
