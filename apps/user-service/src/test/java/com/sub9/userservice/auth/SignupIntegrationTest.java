package com.sub9.userservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.application.service.SignupService;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.auth.presentation.request.CreatorSignupRequest;
import com.sub9.userservice.auth.presentation.request.CustomerSignupRequest;
import com.sub9.userservice.auth.presentation.response.CreatorSignupResponse;
import com.sub9.userservice.auth.presentation.response.CustomerSignupResponse;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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
@DisplayName("회원가입 PostgreSQL 통합")
class SignupIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("signup_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private SignupService signupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreatorRepository creatorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("CUSTOMER 가입은 정규화된 사용자와 BCrypt 해시를 저장한다")
    void when_customer_signs_up_then_normalized_values_and_bcrypt_hash_are_persisted() {
        CustomerSignupResponse response = signupService.signupCustomer(new CustomerSignupRequest(
                " Customer@Example.COM ",
                "Password123!",
                " customer ",
                "010-1234-5678",
                " 서울시 예시구 ",
                "   "));

        User saved = userRepository.findActiveById(response.userId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("customer@example.com");
        assertThat(saved.getPhone()).isEqualTo("01012345678");
        assertThat(saved.getSlackId()).isNull();
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getPassword()).isNotEqualTo("Password123!");
        assertThat(passwordEncoder.matches("Password123!", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("창작자 저장이 실패하면 먼저 저장한 사용자도 롤백한다")
    void when_creator_persistence_fails_then_user_is_rolled_back() {
        String email = "rollback@example.com";
        CreatorSignupRequest request = new CreatorSignupRequest(
                email,
                "Password123!",
                "rollback-user",
                "010-9999-9999",
                "서울시 예시구",
                null,
                "가".repeat(101),
                "123-45-67890");

        assertThatThrownBy(() -> signupService.signupCreator(request))
                .isInstanceOf(BusinessException.class);
        assertThat(userRepository.findActiveByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("삭제된 사용자와 창작자의 고유 값도 다시 사용할 수 없다")
    void when_user_and_creator_are_soft_deleted_then_unique_values_cannot_be_reused() {
        CreatorSignupRequest originalRequest = creatorRequest(
                "deleted@example.com", "deleted-user", "010-1111-2222",
                "삭제상점", "111-22-33333");
        CreatorSignupResponse original = signupService.signupCreator(originalRequest);
        User user = userRepository.findActiveById(original.userId()).orElseThrow();
        Creator creator = creatorRepository.findActiveById(original.creatorId()).orElseThrow();
        Instant deletedAt = Instant.parse("2026-09-01T07:00:00Z");
        creator.softDelete(user.getId(), deletedAt);
        user.softDelete(user.getId(), deletedAt);
        creatorRepository.save(creator);
        userRepository.save(user);
        creatorRepository.flush();

        assertDuplicate(
                () -> signupService.signupCustomer(customerRequest(
                        "deleted@example.com", "new-nickname-1", "010-2000-0001")),
                UserErrorCode.EMAIL_ALREADY_EXISTS);
        assertDuplicate(
                () -> signupService.signupCustomer(customerRequest(
                        "new-email-2@example.com", "deleted-user", "010-2000-0002")),
                UserErrorCode.NICKNAME_ALREADY_EXISTS);
        assertDuplicate(
                () -> signupService.signupCustomer(customerRequest(
                        "new-email-3@example.com", "new-nickname-3", "010-1111-2222")),
                UserErrorCode.PHONE_ALREADY_EXISTS);
        assertDuplicate(
                () -> signupService.signupCreator(creatorRequest(
                        "new-email-4@example.com", "new-nickname-4", "010-2000-0004",
                        "삭제상점", "444-55-66666")),
                UserErrorCode.CREATOR_NAME_ALREADY_EXISTS);
        assertDuplicate(
                () -> signupService.signupCreator(creatorRequest(
                        "new-email-5@example.com", "new-nickname-5", "010-2000-0005",
                        "새로운상점", "111-22-33333")),
                UserErrorCode.BUSINESS_REGISTRATION_NUMBER_ALREADY_EXISTS);
    }

    private CustomerSignupRequest customerRequest(String email, String nickname, String phone) {
        return new CustomerSignupRequest(
                email, "Password123!", nickname, phone, "서울시 예시구", null);
    }

    private CreatorSignupRequest creatorRequest(String email, String nickname, String phone,
            String creatorName, String businessRegistrationNumber) {
        return new CreatorSignupRequest(
                email,
                "Password123!",
                nickname,
                phone,
                "서울시 예시구",
                null,
                creatorName,
                businessRegistrationNumber);
    }

    private void assertDuplicate(Runnable signup, UserErrorCode expectedErrorCode) {
        assertThatThrownBy(signup::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
    }
}
