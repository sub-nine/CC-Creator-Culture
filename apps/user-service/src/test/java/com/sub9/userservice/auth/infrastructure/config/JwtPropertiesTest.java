package com.sub9.userservice.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("JWT 환경 설정")
class JwtPropertiesTest {

    private static final String TEST_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtPropertiesConfig.class)
            .withPropertyValues(
                    "auth.jwt.secret=" + TEST_SECRET,
                    "auth.jwt.access-token-expiration=30m",
                    "auth.jwt.refresh-token-expiration=7d",
                    "auth.jwt.clock-skew=60s");

    @Test
    @DisplayName("JWT 계약 값을 타입 안전한 설정으로 바인딩한다")
    void when_jwt_properties_are_configured_then_binds_contract_values() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            JwtProperties properties = context.getBean(JwtProperties.class);
            assertThat(properties.secret()).isEqualTo(TEST_SECRET);
            assertThat(properties.accessTokenExpiration()).isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.refreshTokenExpiration()).isEqualTo(Duration.ofDays(7));
            assertThat(properties.clockSkew()).isEqualTo(Duration.ofSeconds(60));
        });
    }

    @Test
    @DisplayName("JWT 비밀키가 비어 있으면 설정 등록에 실패한다")
    void when_jwt_secret_is_blank_then_context_fails() {
        contextRunner
                .withPropertyValues("auth.jwt.secret= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("auth.jwt");
                });
    }

    @Test
    @DisplayName("토큰 만료 시간이 0이면 설정 등록에 실패한다")
    void when_token_expiration_is_zero_then_context_fails() {
        contextRunner
                .withPropertyValues("auth.jwt.access-token-expiration=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("auth.jwt");
                });
    }

    @Test
    @DisplayName("시계 오차가 음수이면 설정 등록에 실패한다")
    void when_clock_skew_is_negative_then_context_fails() {
        contextRunner
                .withPropertyValues("auth.jwt.clock-skew=-1s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("auth.jwt");
                });
    }
}
