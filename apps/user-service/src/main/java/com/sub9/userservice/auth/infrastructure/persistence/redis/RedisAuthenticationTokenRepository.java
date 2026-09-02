package com.sub9.userservice.auth.infrastructure.persistence.redis;

import com.sub9.userservice.auth.domain.exception.AuthenticationTokenStorageException;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
// StringRedisTemplate으로 Refresh Token을 저장·조회·삭제하는 Redis 저장소 구현체
public class RedisAuthenticationTokenRepository implements AuthenticationTokenRepository {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveRefreshToken(UUID userId, String refreshToken, Duration ttl) {
        try {
            redisTemplate.opsForValue()
                    .set(AuthenticationTokenRedisKey.refreshToken(userId), refreshToken, ttl);
        } catch (DataAccessException exception) {
            throw new AuthenticationTokenStorageException();
        }
    }

    @Override
    public Optional<String> findRefreshToken(UUID userId) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue()
                    .get(AuthenticationTokenRedisKey.refreshToken(userId)));
        } catch (DataAccessException exception) {
            throw new AuthenticationTokenStorageException();
        }
    }

    @Override
    public void deleteRefreshToken(UUID userId) {
        try {
            redisTemplate.delete(AuthenticationTokenRedisKey.refreshToken(userId));
        } catch (DataAccessException exception) {
            throw new AuthenticationTokenStorageException();
        }
    }
}
