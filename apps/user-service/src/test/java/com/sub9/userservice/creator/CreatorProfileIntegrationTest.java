package com.sub9.userservice.creator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.creator.application.service.CreatorProfileService;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.presentation.request.UpdateCreatorRequest;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
@DisplayName("내 창작자 정보 수정 PostgreSQL 통합")
class CreatorProfileIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T01:00:00Z");
    private static final Instant APPROVED_AT = Instant.parse("2026-09-10T01:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("creator_profile_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private CreatorProfileService creatorProfileService;

    @Autowired
    private CreatorRepository creatorRepository;

    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    @DisplayName("수정한 창작자 정보는 저장되고 기존 승인 기록은 유지된다")
    void when_creator_profile_is_updated_values_are_persisted_and_approval_is_preserved() {
        User admin = saveUser("admin@example.com", "admin", "01010000000", UserRole.MASTER);
        Creator creator = saveApprovedCreator(
                admin.getId(), "기존상점", "1234567890", false);
        UUID approvedBy = creator.getApprovedBy();
        LocalDateTime approvedAt = creator.getApprovedAt();
        UUID createdBy = creator.getCreatedBy();
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setCreatorName("  변경상점  ");
        request.setBusinessRegistrationNumber("987-65-43210");

        var response = creatorProfileService.updateMyCreator(creator.getUserId(), request);

        assertThat(response.creatorName()).isEqualTo("변경상점");
        assertThat(response.businessRegistrationNumber()).isEqualTo("9876543210");
        assertThat(creator.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(creator.getApprovedBy()).isEqualTo(approvedBy);
        assertThat(creator.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(creator.getCreatedBy()).isEqualTo(createdBy);
        assertThat(creator.getUpdatedBy()).isEqualTo(creator.getUserId());
    }

    @Test
    @DisplayName("삭제된 창작자가 사용한 상호명도 재사용할 수 없다")
    void when_deleted_creator_owns_name_update_returns_conflict() {
        User admin = saveUser(
                "duplicate-admin@example.com", "duplicate-admin", "01020000000",
                UserRole.MASTER);
        Creator active = saveApprovedCreator(
                admin.getId(), "활성상점", "2111111111", false);
        saveApprovedCreator(admin.getId(), "예약상점", "2222222222", true);
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setCreatorName("예약상점");

        assertThatThrownBy(() ->
                creatorProfileService.updateMyCreator(active.getUserId(), request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(UserErrorCode.CREATOR_NAME_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("승인되지 않았거나 삭제된 창작자는 수정할 수 없다")
    void when_creator_is_not_approved_or_active_update_returns_not_found() {
        User admin = saveUser(
                "state-admin@example.com", "state-admin", "01030000000",
                UserRole.MASTER);
        Creator pending = savePendingCreator("대기상점", "3111111111");
        Creator rejected = savePendingCreator("거절상점", "3222222222");
        rejected.reject(admin.getId(), APPROVED_AT);
        creatorRepository.flush();
        Creator deleted = saveApprovedCreator(
                admin.getId(), "삭제상점", "3333333333", true);
        UpdateCreatorRequest request = new UpdateCreatorRequest();
        request.setCreatorName("변경상점");

        assertCreatorNotFound(pending.getUserId(), request);
        assertCreatorNotFound(rejected.getUserId(), request);
        assertCreatorNotFound(deleted.getUserId(), request);
    }

    private Creator saveApprovedCreator(
            UUID adminId, String creatorName, String businessNumber, boolean deleted) {
        Creator creator = savePendingCreator(creatorName, businessNumber);
        creator.approve(adminId, APPROVED_AT);
        if (deleted) {
            creator.softDelete(creator.getUserId(), APPROVED_AT.plusSeconds(10));
        }
        creatorRepository.flush();
        return creator;
    }

    private Creator savePendingCreator(String creatorName, String businessNumber) {
        User user = saveUser(
                creatorName + "@example.com",
                creatorName,
                "010" + businessNumber.substring(0, 8),
                UserRole.CREATOR);
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), user.getId(), creatorName, businessNumber, CREATED_AT);
        creatorRepository.save(creator);
        creatorRepository.flush();
        return creator;
    }

    private User saveUser(String email, String nickname, String phone, UserRole role) {
        User user = User.create(
                uuidGenerator.generate(), email, "encoded-password", nickname, phone,
                "서울시 예시구", null, role, CREATED_AT);
        userRepository.save(user);
        userRepository.flush();
        return user;
    }

    private void assertCreatorNotFound(UUID userId, UpdateCreatorRequest request) {
        assertThatThrownBy(() -> creatorProfileService.updateMyCreator(userId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_NOT_FOUND));
    }
}
