package com.sub9.gateway.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.gateway.auth.domain.exception.InvalidAccessTokenException;
import com.sub9.gateway.auth.domain.model.AccessTokenClaims;
import com.sub9.gateway.auth.domain.model.GatewayUserRole;
import com.sub9.gateway.auth.infrastructure.config.JwtProperties;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JWT Access Token 검증")
class JwtAccessTokenValidatorTest {

    private static final Instant NOW = Instant.parse("2026-09-09T04:00:00Z");
    private static final UUID USER_ID = UUID.fromString("01992d35-8600-7000-8000-000000000001");
    private static final UUID TOKEN_ID = UUID.fromString("01992d35-8600-7000-8000-000000000002");
    private static final String SECRET = encodedSecret(32, (byte) 1);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofSeconds(60));
    private final JwtAccessTokenValidator validator = validator(SECRET, properties, clock);

    @Test
    @DisplayName("정상 Access Token의 검증된 Claim을 반환한다")
    void when_access_token_is_valid_then_returns_verified_claims() {
        String token = signedToken(
                signingKey(SECRET), USER_ID.toString(), GatewayUserRole.CUSTOMER.name(),
                TOKEN_ID.toString(), NOW, NOW.plusSeconds(1800), "ACCESS", Jwts.SIG.HS256);

        AccessTokenClaims claims = validator.validate(token);

        assertThat(claims.userId()).isEqualTo(USER_ID);
        assertThat(claims.role()).isEqualTo(GatewayUserRole.CUSTOMER);
        assertThat(claims.tokenId()).isEqualTo(TOKEN_ID);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰을 거부하고 토큰 원문을 노출하지 않는다")
    void when_token_is_signed_with_different_key_then_rejects_without_exposing_token() {
        String token = signedToken(
                signingKey(encodedSecret(32, (byte) 2)), USER_ID.toString(),
                GatewayUserRole.CUSTOMER.name(), TOKEN_ID.toString(), NOW,
                NOW.plusSeconds(1800), "ACCESS", Jwts.SIG.HS256);

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InvalidAccessTokenException.class)
                .hasMessage("유효하지 않은 인증 토큰입니다.")
                .hasMessageNotContaining(token);
    }

    @Test
    @DisplayName("HS256 이외 알고리즘으로 서명한 토큰을 거부한다")
    void when_token_uses_non_hs256_algorithm_then_rejects_token() {
        String hs384Secret = encodedSecret(48, (byte) 3);
        String token = signedToken(
                signingKey(hs384Secret), USER_ID.toString(), GatewayUserRole.CUSTOMER.name(),
                TOKEN_ID.toString(), NOW, NOW.plusSeconds(1800), "ACCESS", Jwts.SIG.HS384);
        JwtAccessTokenValidator hs384KeyValidator = validator(
                hs384Secret, new JwtProperties(hs384Secret, Duration.ofSeconds(60)), clock);

        assertThatThrownBy(() -> hs384KeyValidator.validate(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("필수 Claim이 누락되면 거부한다")
    void when_required_claim_is_missing_then_rejects_token() {
        String token = signedToken(
                signingKey(SECRET), USER_ID.toString(), null, TOKEN_ID.toString(),
                NOW, NOW.plusSeconds(1800), "ACCESS", Jwts.SIG.HS256);

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("사용자 ID나 토큰 ID가 UUID 형식이 아니면 거부한다")
    void when_identifier_claim_is_not_uuid_then_rejects_token() {
        String invalidUserId = signedToken(
                signingKey(SECRET), "not-a-uuid", GatewayUserRole.CUSTOMER.name(),
                TOKEN_ID.toString(), NOW, NOW.plusSeconds(1800), "ACCESS", Jwts.SIG.HS256);
        String invalidTokenId = signedToken(
                signingKey(SECRET), USER_ID.toString(), GatewayUserRole.CUSTOMER.name(),
                "not-a-uuid", NOW, NOW.plusSeconds(1800), "ACCESS", Jwts.SIG.HS256);

        assertThatThrownBy(() -> validator.validate(invalidUserId))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> validator.validate(invalidTokenId))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("허용하지 않은 역할을 거부한다")
    void when_role_is_unknown_then_rejects_token() {
        String token = signedToken(
                signingKey(SECRET), USER_ID.toString(), "UNKNOWN", TOKEN_ID.toString(),
                NOW, NOW.plusSeconds(1800), "ACCESS", Jwts.SIG.HS256);

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("Refresh Token과 알 수 없는 토큰 타입을 거부한다")
    void when_token_type_is_not_access_then_rejects_token() {
        String refreshToken = signedToken(
                signingKey(SECRET), USER_ID.toString(), GatewayUserRole.CUSTOMER.name(),
                TOKEN_ID.toString(), NOW, NOW.plusSeconds(1800), "REFRESH", Jwts.SIG.HS256);
        String unknownToken = signedToken(
                signingKey(SECRET), USER_ID.toString(), GatewayUserRole.CUSTOMER.name(),
                TOKEN_ID.toString(), NOW, NOW.plusSeconds(1800), "UNKNOWN", Jwts.SIG.HS256);

        assertThatThrownBy(() -> validator.validate(refreshToken))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(() -> validator.validate(unknownToken))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("만료 후 60초 이내에는 허용하고 보정 시간이 지나면 거부한다")
    void when_token_is_expired_then_applies_sixty_second_clock_skew() {
        String token = signedToken(
                signingKey(SECRET), USER_ID.toString(), GatewayUserRole.CUSTOMER.name(),
                TOKEN_ID.toString(), NOW, NOW.plusSeconds(1800), "ACCESS", Jwts.SIG.HS256);
        JwtAccessTokenValidator withinSkew = validator(
                SECRET, properties, Clock.fixed(NOW.plusSeconds(1859), ZoneOffset.UTC));
        JwtAccessTokenValidator afterSkew = validator(
                SECRET, properties, Clock.fixed(NOW.plusSeconds(1861), ZoneOffset.UTC));

        assertThat(withinSkew.validate(token).userId()).isEqualTo(USER_ID);
        assertThatThrownBy(() -> afterSkew.validate(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("발급 시각이 허용 오차보다 미래이면 거부한다")
    void when_issued_at_exceeds_clock_skew_then_rejects_token() {
        String token = signedToken(
                signingKey(SECRET), USER_ID.toString(), GatewayUserRole.CUSTOMER.name(),
                TOKEN_ID.toString(), NOW.plusSeconds(61), NOW.plusSeconds(1800),
                "ACCESS", Jwts.SIG.HS256);

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("만료 시각이 발급 시각보다 늦지 않으면 거부한다")
    void when_expiration_is_not_after_issued_at_then_rejects_token() {
        String token = signedToken(
                signingKey(SECRET), USER_ID.toString(), GatewayUserRole.CUSTOMER.name(),
                TOKEN_ID.toString(), NOW, NOW, "ACCESS", Jwts.SIG.HS256);

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    @DisplayName("빈 토큰을 거부한다")
    void when_token_is_blank_then_rejects_token() {
        assertThatThrownBy(() -> validator.validate(" "))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    private JwtAccessTokenValidator validator(
            String encodedSecret,
            JwtProperties jwtProperties,
            Clock validatorClock) {
        SecretKey key = signingKey(encodedSecret);
        JwtParser parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(validatorClock.instant()))
                .clockSkewSeconds(jwtProperties.clockSkew().toSeconds())
                .sig()
                .clear()
                .add(Jwts.SIG.HS256)
                .and()
                .build();
        return new JwtAccessTokenValidator(parser, jwtProperties, validatorClock);
    }

    private static String signedToken(
            SecretKey key,
            String subject,
            String role,
            String tokenId,
            Instant issuedAt,
            Instant expiresAt,
            String tokenType,
            io.jsonwebtoken.security.SecureDigestAlgorithm<SecretKey, ?> algorithm) {
        var builder = Jwts.builder()
                .subject(subject)
                .id(tokenId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("type", tokenType);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key, algorithm).compact();
    }

    private static SecretKey signingKey(String secret) {
        return Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret));
    }

    private static String encodedSecret(int size, byte value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, value);
        return Encoders.BASE64.encode(bytes);
    }
}
