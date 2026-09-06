package com.sub9.orderservice.coupon.infrastructure.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class CouponIssueReleaseScript {

    private final RedisScript<Long> script;

    public CouponIssueReleaseScript() {
        DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>();
        releaseScript.setLocation(new ClassPathResource("redis/coupon-release.lua"));
        releaseScript.setResultType(Long.class);
        this.script = releaseScript;
    }

    public RedisScript<Long> value() {
        return script;
    }
}
