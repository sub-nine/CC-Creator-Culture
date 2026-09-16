package com.sub9.productservice.leaderboard.infrastructure.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class LeaderboardRedisScriptConfig {
    @Bean
    public RedisScript<Long> incrementScoreIfNotProcessedScript() {
        return RedisScript.of(new ClassPathResource("redis/increment_score_if_not_processed.lua"), Long.class);
    }

    @Bean
    public RedisScript<List> snapshotAndClearScript() {
        return RedisScript.of(new ClassPathResource("redis/snapshot_and_clear.lua"), List.class);
    }
}
