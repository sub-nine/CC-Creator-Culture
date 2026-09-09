package com.sub9.gateway.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("인증 토큰 Redis 키")
class AuthenticationTokenRedisKeyTest {

    @Test
    @DisplayName("Access Token ID로 로그아웃 블랙리스트 키를 생성한다")
    void when_token_id_is_given_then_creates_access_token_blacklist_key() {
        UUID tokenId = UUID.fromString("01992d35-8600-7000-8000-000000000002");

        String key = AuthenticationTokenRedisKey.accessTokenBlacklist(tokenId);

        assertThat(key).isEqualTo("auth:blacklist:access:" + tokenId);
    }

    @Test
    @DisplayName("서로 다른 Access Token ID는 서로 다른 블랙리스트 키를 생성한다")
    void when_token_ids_are_different_then_creates_different_keys() {
        UUID first = UUID.fromString("01992d35-8600-7000-8000-000000000002");
        UUID second = UUID.fromString("01992d35-8600-7000-8000-000000000003");

        assertThat(AuthenticationTokenRedisKey.accessTokenBlacklist(first))
                .isNotEqualTo(AuthenticationTokenRedisKey.accessTokenBlacklist(second));
    }
}
