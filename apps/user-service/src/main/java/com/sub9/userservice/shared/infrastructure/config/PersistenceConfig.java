package com.sub9.userservice.shared.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfig {

    @Bean
    Clock utcClock() {
        // 모든 유스케이스가 같은 UTC 기준 시각을 사용하도록 Clock을 하나의 Bean으로 제공한다.
        return Clock.systemUTC();
    }
}
