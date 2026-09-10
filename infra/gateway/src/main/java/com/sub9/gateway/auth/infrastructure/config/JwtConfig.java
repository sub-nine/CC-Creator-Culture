package com.sub9.gateway.auth.infrastructure.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
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

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtParser jwtParser(SecretKey jwtSigningKey, JwtProperties properties, Clock clock) {
        return Jwts.parser()
                .verifyWith(jwtSigningKey)
                .clock(() -> java.util.Date.from(clock.instant()))
                .clockSkewSeconds(properties.clockSkew().toSeconds())
                .sig()
                .clear()
                .add(Jwts.SIG.HS256)
                .and()
                .build();
    }
}
