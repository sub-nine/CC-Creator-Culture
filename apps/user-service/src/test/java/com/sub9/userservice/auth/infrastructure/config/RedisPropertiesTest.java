package com.sub9.userservice.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("Redis 환경 설정")
class RedisPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisPropertiesConfig.class)
            .withPropertyValues(
                    "spring.data.redis.host=127.0.0.1",
                    "spring.data.redis.port=6379",
                    "spring.data.redis.connect-timeout=2s",
                    "spring.data.redis.timeout=2s");

    @Test
    @DisplayName("Redis 연결 정보와 timeout 계약을 바인딩한다")
    void when_redis_properties_are_configured_then_binds_connection_contract() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            DataRedisProperties properties = context.getBean(DataRedisProperties.class);
            assertThat(properties.getHost()).isEqualTo("127.0.0.1");
            assertThat(properties.getPort()).isEqualTo(6379);
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(2));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DataRedisProperties.class)
    static class RedisPropertiesConfig {
    }
}
