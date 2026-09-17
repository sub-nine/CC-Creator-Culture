package com.sub9.userservice.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.user.application.service.UserProfileService;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import com.sub9.userservice.user.presentation.request.UpdateMyProfileRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        "spring.jpa.properties.hibernate.default_schema=public",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("내 정보 조회 PostgreSQL 통합")
class UserProfileIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-14T01:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("user_profile_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("저장된 활성 사용자의 단일 주소를 포함한 내 정보를 조회한다")
    void when_active_user_is_saved_get_my_profile_returns_persisted_information() {
        User user = createUser("profile@example.com", "profile-user", "01011112222");
        userRepository.save(user);
        userRepository.flush();

        var response = userProfileService.getMyProfile(user.getId());

        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.address()).isEqualTo("서울시 예시구 통합로 1");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("Soft Delete된 사용자는 내 정보 조회 대상에서 제외한다")
    void when_user_is_soft_deleted_get_my_profile_returns_not_found() {
        User user = createUser("deleted-profile@example.com", "deleted-profile", "01033334444");
        user.softDelete(user.getId(), CREATED_AT.plusSeconds(60));
        userRepository.save(user);
        userRepository.flush();

        assertThatThrownBy(() -> userProfileService.getMyProfile(user.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("수정한 내 정보가 PostgreSQL에 반영된다")
    void when_profile_is_updated_changes_are_persisted() {
        User user = createUser("update-profile@example.com", "update-profile", "01055556666");
        userRepository.save(user);
        userRepository.flush();
        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setNickname("updated-profile");
        request.setAddress("부산시 예시구 통합로 2");
        request.setSlackId(null);

        userProfileService.updateMyProfile(user.getId(), request);

        var response = userProfileService.getMyProfile(user.getId());
        assertThat(response.nickname()).isEqualTo("updated-profile");
        assertThat(response.phone()).isEqualTo("01055556666");
        assertThat(response.address()).isEqualTo("부산시 예시구 통합로 2");
        assertThat(response.slackId()).isNull();
    }

    @Test
    @DisplayName("탈퇴 회원이 사용한 닉네임으로 수정할 수 없다")
    void when_deleted_user_has_nickname_update_my_profile_returns_conflict() {
        User activeUser = createUser(
                "active-update@example.com", "active-update", "01066667777");
        User deletedUser = createUser(
                "deleted-update@example.com", "reserved-nickname", "01077778888");
        deletedUser.softDelete(deletedUser.getId(), CREATED_AT.plusSeconds(60));
        userRepository.save(activeUser);
        userRepository.save(deletedUser);
        userRepository.flush();
        UpdateMyProfileRequest request = new UpdateMyProfileRequest();
        request.setNickname("reserved-nickname");

        assertThatThrownBy(() -> userProfileService.updateMyProfile(activeUser.getId(), request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(UserErrorCode.NICKNAME_ALREADY_EXISTS));
    }

    private User createUser(String email, String nickname, String phone) {
        UUID userId = uuidGenerator.generate();
        return User.create(
                userId, email, "encoded-password", nickname, phone,
                "서울시 예시구 통합로 1", null, UserRole.CUSTOMER, CREATED_AT);
    }
}
