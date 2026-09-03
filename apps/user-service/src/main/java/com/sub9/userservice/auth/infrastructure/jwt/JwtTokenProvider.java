package com.sub9.userservice.auth.infrastructure.jwt;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.domain.exception.InvalidTokenException;
import com.sub9.userservice.auth.domain.model.TokenClaims;
import com.sub9.userservice.auth.domain.model.TokenType;
import com.sub9.userservice.auth.infrastructure.config.JwtProperties;
import com.sub9.userservice.user.domain.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
// JWT 비밀키가 설정된 환경에서만 JWT 발급·검증 구현체를 Spring Bean으로 등록한다.
public class JwtTokenProvider implements TokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TYPE_CLAIM = "type";
    private static final String INVALID_SECRET_MESSAGE = "JWT 비밀키 설정이 유효하지 않습니다.";

    private final JwtProperties properties;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;
    private final SecretKey signingKey;
    private final JwtParser jwtParser;

    public JwtTokenProvider(
            JwtProperties properties,
            UuidV7Generator uuidV7Generator,
            Clock clock) {
        this.properties = properties;
        this.uuidV7Generator = uuidV7Generator;
        this.clock = clock;
        this.signingKey = createSigningKey(properties.secret());
        validateDurations(properties);
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(properties.clockSkew().toSeconds())
                .sig()
                .clear()
                .add(Jwts.SIG.HS256)
                .and()
                .build();
    }

    @Override
    public String issueAccessToken(UUID userId, UserRole role) {
        return issueToken(userId, role, TokenType.ACCESS, properties.accessTokenExpiration());
    }

    @Override
    public String issueRefreshToken(UUID userId, UserRole role) {
        return issueToken(userId, role, TokenType.REFRESH, properties.refreshTokenExpiration());
    }

    @Override
    public Duration accessTokenExpiration() {
        return properties.accessTokenExpiration();
    }

    @Override
    public Duration refreshTokenExpiration() {
        return properties.refreshTokenExpiration();
    }

    @Override
    public TokenClaims validateAccessToken(String token) {
        return validateToken(token, TokenType.ACCESS);
    }

    @Override
    public TokenClaims validateRefreshToken(String token) {
        return validateToken(token, TokenType.REFRESH);
    }

    private String issueToken(
            UUID userId,
            UserRole role,
            TokenType tokenType,
            Duration validity) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(validity.toSeconds());
        UUID tokenId = uuidV7Generator.generate();

        return Jwts.builder()
                .subject(userId.toString())
                .claim(ROLE_CLAIM, role.name())
                .id(tokenId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(TYPE_CLAIM, tokenType.name())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private TokenClaims validateToken(String token, TokenType expectedType) {
        if (!StringUtils.hasText(token)) {
            throw new InvalidTokenException();
        }

        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            TokenClaims tokenClaims = toTokenClaims(claims);
            validateClaims(tokenClaims, expectedType);
            return tokenClaims;
        } catch (InvalidTokenException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException();
        }
    }

    private TokenClaims toTokenClaims(Claims claims) {
        try {
            return new TokenClaims(
                    UUID.fromString(requiredText(claims.getSubject())),
                    UserRole.valueOf(requiredText(claims.get(ROLE_CLAIM, String.class))),
                    UUID.fromString(requiredText(claims.getId())),
                    requiredDate(claims.getIssuedAt()).toInstant(),
                    requiredDate(claims.getExpiration()).toInstant(),
                    TokenType.valueOf(requiredText(claims.get(TYPE_CLAIM, String.class))));
        } catch (RuntimeException exception) {
            throw new InvalidTokenException();
        }
    }

    private void validateClaims(TokenClaims claims, TokenType expectedType) {
        Instant now = clock.instant();
        Instant latestAllowedIssuedAt = now.plus(properties.clockSkew());

        if (claims.tokenType() != expectedType
                || claims.issuedAt().isAfter(latestAllowedIssuedAt)
                || !claims.expiresAt().isAfter(claims.issuedAt())) {
            throw new InvalidTokenException();
        }
    }

    private String requiredText(String value) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidTokenException();
        }
        return value;
    }

    private Date requiredDate(Date value) {
        if (value == null) {
            throw new InvalidTokenException();
        }
        return value;
    }

    private SecretKey createSigningKey(String encodedSecret) {
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(encodedSecret));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(INVALID_SECRET_MESSAGE);
        }
    }

    private void validateDurations(JwtProperties jwtProperties) {
        if (jwtProperties.accessTokenExpiration().toSeconds() < 1
                || jwtProperties.refreshTokenExpiration().toSeconds() < 1) {
            throw new IllegalStateException("JWT 토큰 유효시간 설정이 유효하지 않습니다.");
        }
    }
}
