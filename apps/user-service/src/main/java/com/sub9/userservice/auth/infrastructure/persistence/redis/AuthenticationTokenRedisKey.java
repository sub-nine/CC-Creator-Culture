package com.sub9.userservice.auth.infrastructure.persistence.redis;

import java.util.UUID;

// 인증 토큰의 Redis 키 형식과 고정 값을 관리하는 유틸리티 클래스
public final class AuthenticationTokenRedisKey {

    public static final String ACCESS_TOKEN_BLACKLIST_VALUE = "logout";

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    private static final String ACCESS_TOKEN_BLACKLIST_PREFIX = "auth:blacklist:access:";

    private AuthenticationTokenRedisKey() {}

    public static String refreshToken(UUID userId) {
        return REFRESH_TOKEN_PREFIX + userId;
    }

    public static String accessTokenBlacklist(UUID tokenId) {
        return ACCESS_TOKEN_BLACKLIST_PREFIX + tokenId;
    }
}
