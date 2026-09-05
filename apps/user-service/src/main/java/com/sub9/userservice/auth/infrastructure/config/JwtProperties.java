package com.sub9.userservice.auth.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration,
        @NotNull Duration clockSkew) {

    @AssertTrue(message = "Access Token 만료 시간은 0보다 커야 합니다.")
    public boolean isAccessTokenExpirationPositive() {
        return accessTokenExpiration == null || accessTokenExpiration.isPositive();
    }

    @AssertTrue(message = "Refresh Token 만료 시간은 0보다 커야 합니다.")
    public boolean isRefreshTokenExpirationPositive() {
        return refreshTokenExpiration == null || refreshTokenExpiration.isPositive();
    }

    @AssertTrue(message = "시계 오차는 음수일 수 없습니다.")
    public boolean isClockSkewNotNegative() {
        return clockSkew == null || !clockSkew.isNegative();
    }
}
