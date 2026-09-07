package com.sub9.orderservice.coupon.infrastructure.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class CouponIssueReserveScript {

    private final RedisScript<Long> script;

    public CouponIssueReserveScript() {
        DefaultRedisScript<Long> reserveScript = new DefaultRedisScript<>();
        reserveScript.setLocation(new ClassPathResource("redis/coupon-reserve.lua"));
        reserveScript.setResultType(Long.class);
        this.script = reserveScript;
    }

    public RedisScript<Long> value() {
        return script;
    }
}
