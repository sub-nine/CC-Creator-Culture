package com.sub9.userservice.auth.infrastructure.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("인증 토큰 Redis 키")
// 인증 토큰 Redis 키와 고정 값의 형식을 검증하는 단위 테스트
class AuthenticationTokenRedisKeyTest {

    private static final UUID ID = UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");

    @Test
    @DisplayName("사용자 ID로 Refresh Token 키를 만든다")
    void creates_refresh_token_key() {
        assertThat(AuthenticationTokenRedisKey.refreshToken(ID))
                .isEqualTo("auth:refresh:0198f2a0-76c0-7000-8000-000000000001");
    }

    @Test
    @DisplayName("jti로 Access Token 블랙리스트 키를 만든다")
    void creates_access_token_blacklist_key() {
        assertThat(AuthenticationTokenRedisKey.accessTokenBlacklist(ID))
                .isEqualTo("auth:blacklist:access:0198f2a0-76c0-7000-8000-000000000001");
        assertThat(AuthenticationTokenRedisKey.ACCESS_TOKEN_BLACKLIST_VALUE).isEqualTo("logout");
    }
}
