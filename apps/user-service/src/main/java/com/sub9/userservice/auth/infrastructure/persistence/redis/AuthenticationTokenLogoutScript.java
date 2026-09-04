package com.sub9.userservice.auth.infrastructure.persistence.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationTokenLogoutScript {
    // 원자적 로그아웃 처리를 위한 Redis Lua Script를 제공

    private final RedisScript<Long> script;

    public AuthenticationTokenLogoutScript() {
        DefaultRedisScript<Long> logoutScript = new DefaultRedisScript<>();
        logoutScript.setLocation(new ClassPathResource("redis/logout.lua"));
        logoutScript.setResultType(Long.class);
        this.script = logoutScript;
    }

    public RedisScript<Long> value() {
        return script;
    }
}
