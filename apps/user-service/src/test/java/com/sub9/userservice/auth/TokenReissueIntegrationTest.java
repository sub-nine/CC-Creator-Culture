package com.sub9.userservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.application.service.LoginService;
import com.sub9.userservice.auth.application.service.SignupService;
import com.sub9.userservice.auth.application.service.TokenReissueService;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import com.sub9.userservice.auth.infrastructure.persistence.redis.AuthenticationTokenRedisKey;
import com.sub9.userservice.auth.presentation.request.CustomerSignupRequest;
import com.sub9.userservice.auth.presentation.request.LoginRequest;
import com.sub9.userservice.auth.presentation.request.TokenReissueRequest;
import com.sub9.userservice.auth.presentation.response.CustomerSignupResponse;
import com.sub9.userservice.auth.presentation.response.LoginResponse;
import com.sub9.userservice.auth.presentation.response.TokenReissueResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=private",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Access Token 재발급 통합")
class TokenReissueIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("token_reissue_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private SignupService signupService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private TokenReissueService tokenReissueService;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AuthenticationTokenRepository authenticationTokenRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("auth.jwt.secret", () -> JWT_SECRET);
        registry.add("auth.jwt.access-token-expiration", () -> "30m");
        registry.add("auth.jwt.refresh-token-expiration", () -> "7d");
        registry.add("auth.jwt.clock-skew", () -> "60s");
    }

    @Test
    @DisplayName("로그인한 사용자는 Refresh Token과 TTL 변경 없이 Access Token을 재발급한다")
    void reissues_access_token_without_rotating_refresh_token() {
        LoginFixture fixture = signupAndLogin(
                "reissue@example.com", "reissue-user", "010-2000-0001");
        String refreshKey = AuthenticationTokenRedisKey.refreshToken(fixture.signup().userId());
        Long ttlBefore = redisTemplate.getExpire(refreshKey);

        TokenReissueResponse response = tokenReissueService.reissue(
                new TokenReissueRequest(fixture.login().refreshToken()));

        assertThat(response.accessToken()).isNotEqualTo(fixture.login().accessToken());
        assertThat(tokenProvider.validateAccessToken(response.accessToken()).userId())
                .isEqualTo(fixture.signup().userId());
        assertThat(authenticationTokenRepository.findRefreshToken(fixture.signup().userId()))
                .contains(fixture.login().refreshToken());
        assertThat(redisTemplate.getExpire(refreshKey)).isLessThanOrEqualTo(ttlBefore);
    }

    @Test
    @DisplayName("재로그인으로 교체된 이전 Refresh Token은 거부한다")
    void rejects_refresh_token_replaced_by_new_login() {
        LoginFixture fixture = signupAndLogin(
                "replaced@example.com", "replaced-user", "010-2000-0002");
        loginService.login(new LoginRequest("replaced@example.com", PASSWORD));

        assertInvalidRefreshToken(fixture.login().refreshToken());
    }

    @Test
    @DisplayName("Redis에서 삭제된 Refresh Token은 거부한다")
    void rejects_deleted_refresh_token() {
        LoginFixture fixture = signupAndLogin(
                "deleted-token@example.com", "deleted-token-user", "010-2000-0003");
        authenticationTokenRepository.deleteRefreshToken(fixture.signup().userId());

        assertInvalidRefreshToken(fixture.login().refreshToken());
    }

    @Test
    @DisplayName("Access Token으로 재발급을 요청하면 거부한다")
    void rejects_access_token_as_refresh_token() {
        LoginFixture fixture = signupAndLogin(
                "access-misuse@example.com", "access-misuse-user", "010-2000-0004");

        assertInvalidRefreshToken(fixture.login().accessToken());
    }

    private LoginFixture signupAndLogin(String email, String nickname, String phone) {
        CustomerSignupResponse signup = signupService.signupCustomer(new CustomerSignupRequest(
                email, PASSWORD, nickname, phone, "서울시 예시구", null));
        LoginResponse login = loginService.login(new LoginRequest(email, PASSWORD));
        return new LoginFixture(signup, login);
    }

    private void assertInvalidRefreshToken(String refreshToken) {
        assertThatThrownBy(() -> tokenReissueService.reissue(new TokenReissueRequest(refreshToken)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    private record LoginFixture(CustomerSignupResponse signup, LoginResponse login) {
    }
}
