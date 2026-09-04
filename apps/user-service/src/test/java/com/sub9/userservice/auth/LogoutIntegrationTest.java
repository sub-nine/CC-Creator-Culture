package com.sub9.userservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.application.service.LoginService;
import com.sub9.userservice.auth.application.service.LogoutService;
import com.sub9.userservice.auth.application.service.SignupService;
import com.sub9.userservice.auth.application.service.TokenReissueService;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.domain.model.TokenClaims;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import com.sub9.userservice.auth.infrastructure.persistence.redis.AuthenticationTokenRedisKey;
import com.sub9.userservice.auth.presentation.request.CustomerSignupRequest;
import com.sub9.userservice.auth.presentation.request.LoginRequest;
import com.sub9.userservice.auth.presentation.request.TokenReissueRequest;
import com.sub9.userservice.auth.presentation.response.CustomerSignupResponse;
import com.sub9.userservice.auth.presentation.response.LoginResponse;
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
@DisplayName("Lua Script 기반 로그아웃 통합")
class LogoutIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("logout_test")
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
    private LogoutService logoutService;
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
    @DisplayName("로그아웃은 Refresh Token을 삭제하고 Access Token을 차단해 재발급을 거부한다")
    void logout_revokes_refresh_and_access_tokens_idempotently() {
        CustomerSignupResponse signup = signupService.signupCustomer(new CustomerSignupRequest(
                "logout@example.com",
                PASSWORD,
                "logout-user",
                "010-3000-0001",
                "서울시 예시구",
                null));
        LoginResponse login = loginService.login(new LoginRequest("logout@example.com", PASSWORD));
        TokenClaims accessClaims = tokenProvider.validateAccessToken(login.accessToken());

        logoutService.logout(
                signup.userId(), accessClaims.tokenId(), accessClaims.expiresAt().getEpochSecond());

        String blacklistKey =
                AuthenticationTokenRedisKey.accessTokenBlacklist(accessClaims.tokenId());
        assertThat(authenticationTokenRepository.findRefreshToken(signup.userId())).isEmpty();
        assertThat(redisTemplate.opsForValue().get(blacklistKey)).isEqualTo("logout");
        assertThat(redisTemplate.getExpire(blacklistKey)).isPositive();
        assertThatThrownBy(() -> tokenReissueService.reissue(
                        new TokenReissueRequest(login.refreshToken())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(AuthErrorCode.INVALID_REFRESH_TOKEN));

        Long ttlBeforeRetry = redisTemplate.getExpire(blacklistKey);
        logoutService.logout(
                signup.userId(), accessClaims.tokenId(), accessClaims.expiresAt().getEpochSecond());
        assertThat(redisTemplate.getExpire(blacklistKey)).isLessThanOrEqualTo(ttlBeforeRetry);
    }
}
