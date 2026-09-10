package com.sub9.gateway.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Gateway JWT 환경 설정")
class JwtConfigTest {

    private static final String VALID_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class)
            .withPropertyValues(
                    "auth.jwt.secret=" + VALID_SECRET,
                    "auth.jwt.clock-skew=60s");

    @Test
    @DisplayName("JWT 계약 값을 바인딩하고 HS256에 사용할 키를 생성한다")
    void when_jwt_properties_are_valid_then_binds_values_and_creates_key() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecretKey.class);

            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.secret()).isEqualTo(VALID_SECRET);
            assertThat(properties.clockSkew()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    @DisplayName("JWT 비밀키가 Base64 형식이 아니면 설정에 실패한다")
    void when_jwt_secret_is_not_base64_then_context_fails() {
        contextRunner
                .withPropertyValues("auth.jwt.secret=not_base64!")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause()
                        .hasMessage("JWT 비밀키 설정이 유효하지 않습니다."));
    }

    @Test
    @DisplayName("JWT 비밀키가 32바이트보다 짧으면 설정에 실패한다")
    void when_jwt_secret_is_too_short_then_context_fails() {
        contextRunner
                .withPropertyValues("auth.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause()
                        .hasMessage("JWT 비밀키 설정이 유효하지 않습니다."));
    }

    @Test
    @DisplayName("시계 오차가 음수이면 설정 바인딩에 실패한다")
    void when_clock_skew_is_negative_then_context_fails() {
        contextRunner
                .withPropertyValues("auth.jwt.clock-skew=-1s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("JWT 비밀키가 없으면 설정 바인딩에 실패한다")
    void when_jwt_secret_is_missing_then_context_fails() {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtConfig.class)
                .withPropertyValues("auth.jwt.clock-skew=60s")
                .run(context -> assertThat(context).hasFailed());
    }
}
