package com.sub9.gateway.auth.infrastructure.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final String INVALID_SECRET_MESSAGE = "JWT 비밀키 설정이 유효하지 않습니다.";

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(INVALID_SECRET_MESSAGE);
        }
    }
}
