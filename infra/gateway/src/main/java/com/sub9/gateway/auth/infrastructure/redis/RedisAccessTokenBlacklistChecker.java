package com.sub9.gateway.auth.infrastructure.redis;

import com.sub9.gateway.auth.domain.exception.AuthenticationServiceUnavailableException;
import com.sub9.gateway.auth.domain.exception.InvalidAccessTokenException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
// ReactiveStringRedisTemplate을 사용해 키 존재 여부를 비동기로 확인 (로그아웃된 JWT인지 검사)
public class RedisAccessTokenBlacklistChecker {

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Void> verifyNotBlacklisted(UUID tokenId) {
        String blacklistKey = AuthenticationTokenRedisKey.accessTokenBlacklist(tokenId);

        return redisTemplate.hasKey(blacklistKey)
                // Redis 연결 실패나 timeout은 인증 서비스 장애로 변환한다.
                .onErrorMap(
                        DataAccessException.class,
                        AuthenticationServiceUnavailableException::new)

                .flatMap(blacklisted -> {
                    // 키가 존재하면 로그아웃으로 무효화된 Access Token이다.
                    if (blacklisted) {
                        return Mono.error(new InvalidAccessTokenException());
                    }

                    // 키가 없으면 유효한 인증 요청이므로 다음 단계로 진행한다.
                    return Mono.empty();
                });
    }
}
