package com.sub9.gateway.auth.infrastructure.jwt;

import com.sub9.gateway.auth.domain.exception.InvalidAccessTokenException;
import com.sub9.gateway.auth.domain.model.AccessTokenClaims;
import com.sub9.gateway.auth.domain.model.GatewayUserRole;
import com.sub9.gateway.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
// JWT 검증에 필요한 Parser, 설정값, 시간 기준을 주입받음 (JWT가 정상인지 검사)
public class JwtAccessTokenValidator {

    private static final String ROLE_CLAIM = "role";
    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";

    private final JwtParser jwtParser;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtAccessTokenValidator(
            JwtParser jwtParser,
            JwtProperties properties,
            Clock clock) {
        this.jwtParser = jwtParser;
        this.properties = properties;
        this.clock = clock;
    }

    // JWT의 서명·만료·필수 Claim과 Access Token 정책을 검증
    public AccessTokenClaims validate(String token) {
        if (!StringUtils.hasText(token)) {
            throw new InvalidAccessTokenException();
        }

        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            AccessTokenClaims accessTokenClaims = toAccessTokenClaims(claims);
            validateClaims(accessTokenClaims, requiredText(claims.get(TYPE_CLAIM, String.class)));
            return accessTokenClaims;
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidAccessTokenException();
        }
    }

    // JWT Claims를 Gateway에서 사용할 AccessTokenClaims 객체로 변환
    private AccessTokenClaims toAccessTokenClaims(Claims claims) {
        return new AccessTokenClaims(
                UUID.fromString(requiredText(claims.getSubject())),
                GatewayUserRole.valueOf(requiredText(claims.get(ROLE_CLAIM, String.class))),
                UUID.fromString(requiredText(claims.getId())),
                requiredDate(claims.getIssuedAt()).toInstant(),
                requiredDate(claims.getExpiration()).toInstant());
    }

    // 토큰 타입과 발급·만료 시각이 Access Token 정책에 맞는지 검증
    private void validateClaims(AccessTokenClaims claims, String tokenType) {
        Instant latestAllowedIssuedAt = clock.instant().plus(properties.clockSkew());

        if (!ACCESS_TOKEN_TYPE.equals(tokenType)
                || claims.issuedAt().isAfter(latestAllowedIssuedAt)
                || !claims.expiresAt().isAfter(claims.issuedAt())) {
            throw new InvalidAccessTokenException();
        }
    }

    private String requiredText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidAccessTokenException();
        }
        return value;
    }

    private Date requiredDate(Date value) {
        if (value == null) {
            throw new InvalidAccessTokenException();
        }
        return value;
    }
}
