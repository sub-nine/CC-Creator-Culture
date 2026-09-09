package com.sub9.gateway.auth.infrastructure.redis;

import java.util.UUID;

// 검증된 토큰 ID를 블랙리스트 Redis 키로 변환
public final class AuthenticationTokenRedisKey {

    private static final String ACCESS_TOKEN_BLACKLIST_PREFIX = "auth:blacklist:access:";

    private AuthenticationTokenRedisKey() {
    }

    public static String accessTokenBlacklist(UUID tokenId) {
        return ACCESS_TOKEN_BLACKLIST_PREFIX + tokenId;
    }
}
