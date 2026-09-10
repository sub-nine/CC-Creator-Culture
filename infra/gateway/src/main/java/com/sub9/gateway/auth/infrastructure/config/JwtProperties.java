package com.sub9.gateway.auth.infrastructure.config;

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
        @NotNull Duration clockSkew) {

    @AssertTrue(message = "시계 오차는 음수일 수 없습니다.")
    public boolean isClockSkewNotNegative() {
        return clockSkew == null || !clockSkew.isNegative();
    }
}
