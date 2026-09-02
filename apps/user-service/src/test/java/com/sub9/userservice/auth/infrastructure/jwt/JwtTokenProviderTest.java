package com.sub9.userservice.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.InvalidTokenException;
import com.sub9.userservice.auth.domain.model.TokenClaims;
import com.sub9.userservice.auth.domain.model.TokenType;
import com.sub9.userservice.auth.infrastructure.config.JwtProperties;
import com.sub9.userservice.user.domain.model.UserRole;
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

@DisplayName("JWT 토큰 Provider")
class JwtTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-09-02T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final String SECRET = encodedSecret(32, (byte) 1);

    private final UuidV7Generator uuidV7Generator = new UuidV7Generator();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtProperties properties = properties(SECRET);
    private final JwtTokenProvider tokenProvider = provider(properties, clock);

    @Test
    @DisplayName("Access Token에 필수 Claim과 30분 만료시간을 담아 발급한다")
    void when_access_token_is_issued_then_contains_required_claims() {
        String token = tokenProvider.issueAccessToken(USER_ID, UserRole.CUSTOMER);
        TokenClaims claims = tokenProvider.validateAccessToken(token);

        assertThat(claims.userId()).isEqualTo(USER_ID);
        assertThat(claims.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(claims.tokenType()).isEqualTo(TokenType.ACCESS);
        assertThat(claims.tokenId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("Refresh Token에 필수 Claim과 7일 만료시간을 담아 발급한다")
    void when_refresh_token_is_issued_then_contains_required_claims() {
        String token = tokenProvider.issueRefreshToken(USER_ID, UserRole.CREATOR);
        TokenClaims claims = tokenProvider.validateRefreshToken(token);

        assertThat(claims.userId()).isEqualTo(USER_ID);
        assertThat(claims.role()).isEqualTo(UserRole.CREATOR);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(604800));
        assertThat(claims.tokenType()).isEqualTo(TokenType.REFRESH);
        assertThat(claims.tokenId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("발급하는 토큰마다 새로운 UUID v7 jti를 사용한다")
    void when_tokens_are_issued_then_each_token_has_distinct_uuid_v7_id() {
        TokenClaims first = tokenProvider.validateAccessToken(
                tokenProvider.issueAccessToken(USER_ID, UserRole.CUSTOMER));
        TokenClaims second = tokenProvider.validateAccessToken(
                tokenProvider.issueAccessToken(USER_ID, UserRole.CUSTOMER));

        assertThat(first.tokenId()).isNotEqualTo(second.tokenId());
        assertThat(first.tokenId().version()).isEqualTo(7);
        assertThat(second.tokenId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("Access와 Refresh Token 종류를 바꾸어 사용하면 거부한다")
    void when_token_type_does_not_match_then_rejects_token() {
        String accessToken = tokenProvider.issueAccessToken(USER_ID, UserRole.CUSTOMER);
        String refreshToken = tokenProvider.issueRefreshToken(USER_ID, UserRole.CUSTOMER);

        assertThatThrownBy(() -> tokenProvider.validateRefreshToken(accessToken))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokenProvider.validateAccessToken(refreshToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰을 거부하고 토큰 원문을 예외에 노출하지 않는다")
    void when_token_is_signed_with_different_key_then_rejects_without_exposing_token() {
        String token = provider(properties(encodedSecret(32, (byte) 2)), clock)
                .issueAccessToken(USER_ID, UserRole.CUSTOMER);

        assertThatThrownBy(() -> tokenProvider.validateAccessToken(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("유효하지 않은 인증 토큰입니다.")
                .hasMessageNotContaining(token);
    }

    @Test
    @DisplayName("같은 키라도 HS256이 아닌 알고리즘으로 서명한 토큰은 거부한다")
    void when_token_uses_non_hs256_algorithm_then_rejects_token() {
        byte[] keyBytes = filledBytes(48, (byte) 3);
        String encodedSecret = Encoders.BASE64.encode(keyBytes);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        JwtTokenProvider provider = provider(properties(encodedSecret), clock);
        String token = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", UserRole.CUSTOMER.name())
                .id(uuidV7Generator.generate().toString())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(1800)))
                .claim("type", TokenType.ACCESS.name())
                .signWith(key, Jwts.SIG.HS384)
                .compact();

        assertThatThrownBy(() -> provider.validateAccessToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("필수 Claim이 누락되거나 형식이 잘못되면 거부한다")
    void when_required_claim_is_missing_or_invalid_then_rejects_token() {
        SecretKey key = signingKey(SECRET);
        String missingRole = signedToken(
                key, USER_ID.toString(), null, uuidV7Generator.generate().toString(),
                NOW, NOW.plusSeconds(1800), TokenType.ACCESS.name());
        String invalidSubject = signedToken(
                key, "not-a-uuid", UserRole.CUSTOMER.name(), uuidV7Generator.generate().toString(),
                NOW, NOW.plusSeconds(1800), TokenType.ACCESS.name());

        assertThatThrownBy(() -> tokenProvider.validateAccessToken(missingRole))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> tokenProvider.validateAccessToken(invalidSubject))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("만료 후 60초 이내에는 허용하고 보정 시간이 지나면 거부한다")
    void when_token_is_expired_then_applies_sixty_second_clock_skew() {
        String token = tokenProvider.issueAccessToken(USER_ID, UserRole.CUSTOMER);
        JwtTokenProvider withinSkew = provider(
                properties, Clock.fixed(NOW.plusSeconds(1800 + 59), ZoneOffset.UTC));
        JwtTokenProvider afterSkew = provider(
                properties, Clock.fixed(NOW.plusSeconds(1800 + 61), ZoneOffset.UTC));

        assertThat(withinSkew.validateAccessToken(token).userId()).isEqualTo(USER_ID);
        assertThatThrownBy(() -> afterSkew.validateAccessToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("발급 시각이 허용 오차보다 미래이면 거부한다")
    void when_issued_at_exceeds_clock_skew_then_rejects_token() {
        String token = signedToken(
                signingKey(SECRET),
                USER_ID.toString(),
                UserRole.CUSTOMER.name(),
                uuidV7Generator.generate().toString(),
                NOW.plusSeconds(61),
                NOW.plusSeconds(1800),
                TokenType.ACCESS.name());

        assertThatThrownBy(() -> tokenProvider.validateAccessToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Base64 형식이 아니거나 32바이트보다 짧은 비밀키를 거부한다")
    void when_secret_is_invalid_or_weak_then_provider_creation_fails() {
        assertThatThrownBy(() -> provider(properties("not-base64%%%"), clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT 비밀키 설정이 유효하지 않습니다.")
                .hasMessageNotContaining("not-base64%%%");
        assertThatThrownBy(() -> provider(properties(encodedSecret(31, (byte) 1)), clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT 비밀키 설정이 유효하지 않습니다.");
    }

    @Test
    @DisplayName("빈 토큰을 거부한다")
    void when_token_is_blank_then_rejects_token() {
        assertThatThrownBy(() -> tokenProvider.validateAccessToken(" "))
                .isInstanceOf(InvalidTokenException.class);
    }

    private JwtTokenProvider provider(JwtProperties jwtProperties, Clock providerClock) {
        return new JwtTokenProvider(jwtProperties, uuidV7Generator, providerClock);
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(
                secret, Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofSeconds(60));
    }

    private static SecretKey signingKey(String secret) {
        return Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret));
    }

    private static String signedToken(
            SecretKey key,
            String subject,
            String role,
            String tokenId,
            Instant issuedAt,
            Instant expiresAt,
            String tokenType) {
        var builder = Jwts.builder()
                .subject(subject)
                .id(tokenId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("type", tokenType);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    private static String encodedSecret(int size, byte value) {
        return Encoders.BASE64.encode(filledBytes(size, value));
    }

    private static byte[] filledBytes(int size, byte value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, value);
        return bytes;
    }
}
