package com.sub9.userservice.auth.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auth.jwt", name = "secret")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtPropertiesConfig {
}
