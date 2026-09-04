package com.sub9.userservice.auth.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
// JWT 비밀키가 설정된 환경에서만 JWT 설정값 바인딩을 활성화한다.
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtPropertiesConfig {
}
