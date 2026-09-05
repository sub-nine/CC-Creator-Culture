package com.sub9.userservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.application.port.output.TokenProvider;
import com.sub9.userservice.auth.application.service.LoginService;
import com.sub9.userservice.auth.application.service.SignupService;
import com.sub9.userservice.auth.domain.exception.AuthErrorCode;
import com.sub9.userservice.auth.domain.repository.AuthenticationTokenRepository;
import com.sub9.userservice.auth.presentation.request.CreatorSignupRequest;
import com.sub9.userservice.auth.presentation.request.CustomerSignupRequest;
import com.sub9.userservice.auth.presentation.request.LoginRequest;
import com.sub9.userservice.auth.presentation.response.CreatorSignupResponse;
import com.sub9.userservice.auth.presentation.response.CustomerSignupResponse;
import com.sub9.userservice.auth.presentation.response.LoginResponse;
import com.sub9.userservice.user.domain.model.UserRole;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
@DisplayName("승인 상태 기반 로그인 통합")
class LoginIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("login_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:7.4.11-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private SignupService signupService;        // 실제 회원가입 로직

    @Autowired
    private LoginService loginService;          // 실제 로그인 로직

    @Autowired
    private TokenProvider tokenProvider;        // 실제 JWT 구현체

    @Autowired
    private AuthenticationTokenRepository authenticationTokenRepository;        // 실제 Redis 구현체

    @Autowired
    private JdbcTemplate jdbcTemplate;          // 실제 PostgreSQL SQL 실행

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
    @DisplayName("CUSTOMER 로그인은 JWT를 발급하고 Refresh Token을 Redis에 저장한다")
    void customer_login_issues_tokens_and_stores_refresh_token() {
        CustomerSignupResponse signup = signupService.signupCustomer(customerRequest(
                "customer-login@example.com", "customer-login", "010-1000-0001"));

        LoginResponse response = loginService.login(
                new LoginRequest(" CUSTOMER-LOGIN@EXAMPLE.COM ", PASSWORD));

        assertThat(tokenProvider.validateAccessToken(response.accessToken()).userId())
                .isEqualTo(signup.userId());
        assertThat(tokenProvider.validateAccessToken(response.accessToken()).role())
                .isEqualTo(UserRole.CUSTOMER);
        assertThat(tokenProvider.validateRefreshToken(response.refreshToken()).userId())
                .isEqualTo(signup.userId());
        // redis 저장 검증
        assertThat(authenticationTokenRepository.findRefreshToken(signup.userId()))
                .contains(response.refreshToken());
        assertThat(response.expiresIn()).isEqualTo(1800);
    }

    @Test
    @DisplayName("재로그인은 기존 Refresh Token을 새 토큰으로 교체한다")
    void repeated_login_replaces_refresh_token() {
        CustomerSignupResponse signup = signupService.signupCustomer(customerRequest(
                "repeat-login@example.com", "repeat-login", "010-1000-0002"));

        LoginResponse first = loginService.login(
                new LoginRequest("repeat-login@example.com", PASSWORD));
        LoginResponse second = loginService.login(
                new LoginRequest("repeat-login@example.com", PASSWORD));

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(authenticationTokenRepository.findRefreshToken(signup.userId()))
                .contains(second.refreshToken());
    }

    @Test
    @DisplayName("PENDING CREATOR는 AUTH_0003으로 차단하고 Redis에 토큰을 저장하지 않는다")
    void pending_creator_is_rejected_without_storing_token() {
        CreatorSignupResponse signup = signupService.signupCreator(creatorRequest(
                "pending-login@example.com", "pending-login", "010-1000-0003",
                "승인대기상점", "111-11-11111"));

        assertBusinessError(
                () -> loginService.login(new LoginRequest("pending-login@example.com", PASSWORD)),
                AuthErrorCode.CREATOR_APPROVAL_PENDING);
        assertThat(authenticationTokenRepository.findRefreshToken(signup.userId())).isEmpty();
    }

    @Test
    @DisplayName("REJECTED CREATOR는 AUTH_0004로 차단한다")
    void rejected_creator_is_rejected() {
        CreatorSignupResponse signup = signupService.signupCreator(creatorRequest(
                "rejected-login@example.com", "rejected-login", "010-1000-0004",
                "승인거절상점", "222-22-22222"));
        updateApprovalStatus(signup, "REJECTED");

        assertBusinessError(
                () -> loginService.login(new LoginRequest("rejected-login@example.com", PASSWORD)),
                AuthErrorCode.CREATOR_APPROVAL_REJECTED);
        assertThat(authenticationTokenRepository.findRefreshToken(signup.userId())).isEmpty();
    }

    @Test
    @DisplayName("APPROVED CREATOR에게 CREATOR JWT를 발급한다")
    void approved_creator_receives_creator_tokens() {
        CreatorSignupResponse signup = signupService.signupCreator(creatorRequest(
                "approved-login@example.com", "approved-login", "010-1000-0005",
                "승인완료상점", "333-33-33333"));
        updateApprovalStatus(signup, "APPROVED");

        LoginResponse response = loginService.login(
                new LoginRequest("approved-login@example.com", PASSWORD));

        assertThat(tokenProvider.validateAccessToken(response.accessToken()).role())
                .isEqualTo(UserRole.CREATOR);
        assertThat(authenticationTokenRepository.findRefreshToken(signup.userId()))
                .contains(response.refreshToken());
    }

    private void updateApprovalStatus(CreatorSignupResponse signup, String status) {
        jdbcTemplate.update(
                "UPDATE private.p_creators SET approval_status = ? WHERE user_id = ?",
                status,
                signup.userId());
    }

    private CustomerSignupRequest customerRequest(String email, String nickname, String phone) {
        return new CustomerSignupRequest(
                email, PASSWORD, nickname, phone, "서울시 예시구", null);
    }

    private CreatorSignupRequest creatorRequest(
            String email,
            String nickname,
            String phone,
            String creatorName,
            String businessRegistrationNumber) {
        return new CreatorSignupRequest(
                email,
                PASSWORD,
                nickname,
                phone,
                "서울시 예시구",
                null,
                creatorName,
                businessRegistrationNumber);
    }

    private void assertBusinessError(Runnable operation, AuthErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isSameAs(errorCode));
    }
}
