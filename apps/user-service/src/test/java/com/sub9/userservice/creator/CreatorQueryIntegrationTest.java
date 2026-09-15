package com.sub9.userservice.creator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.application.service.CreatorQueryService;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Instant;
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
@DisplayName("창작자 목록·단건 조회 PostgreSQL 통합")
class CreatorQueryIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-15T01:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("creator_query_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private CreatorQueryService creatorQueryService;

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
    @DisplayName("목록은 승인되고 사용자와 창작자가 모두 활성인 대상만 검색하여 반환한다")
    void when_creators_are_searched_only_approved_active_creators_are_returned() {
        User admin = saveUser("admin@example.com", "admin", "01010000000", UserRole.MASTER);
        Creator alpha = saveApprovedCreator(
                admin.getId(), "알파트렌드", "1111111111", false, false);
        saveApprovedCreator(admin.getId(), "베타상점", "2222222222", false, false);
        saveApprovedCreator(admin.getId(), "삭제트렌드", "3333333333", true, false);
        saveApprovedCreator(admin.getId(), "탈퇴트렌드", "4444444444", false, true);
        savePendingCreator("대기트렌드", "5555555555");

        var response = creatorQueryService.getCreators(
                0, 20, " 트렌드 ", "creatorName,asc");

        assertThat(response.content())
                .extracting(item -> item.creatorId())
                .containsExactly(alpha.getId());
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("목록은 상호명 기준 내림차순과 페이지 크기를 적용한다")
    void when_creator_page_is_requested_sort_and_paging_are_applied() {
        User admin = saveUser("sort-admin@example.com", "sort-admin", "01020000000",
                UserRole.MASTER);
        saveApprovedCreator(admin.getId(), "가게가", "6111111111", false, false);
        saveApprovedCreator(admin.getId(), "가게나", "6222222222", false, false);

        var response = creatorQueryService.getCreators(
                0, 1, null, "creatorName,desc");

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().creatorName()).isEqualTo("가게나");
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("단건 조회는 승인되고 사용자와 창작자가 모두 활성인 경우에만 성공한다")
    void when_creator_detail_is_requested_only_approved_active_creator_is_returned() {
        User admin = saveUser("detail-admin@example.com", "detail-admin", "01030000000",
                UserRole.MASTER);
        Creator active = saveApprovedCreator(
                admin.getId(), "활성상점", "7111111111", false, false);
        Creator deletedUserCreator = saveApprovedCreator(
                admin.getId(), "탈퇴상점", "7222222222", false, true);

        assertThat(creatorQueryService.getCreator(active.getId()).creatorName())
                .isEqualTo("활성상점");
        assertThatThrownBy(() -> creatorQueryService.getCreator(deletedUserCreator.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_NOT_FOUND));
    }

    @Test
    @DisplayName("내 창작자 조회는 승인되고 사용자와 창작자가 모두 활성인 경우에만 성공한다")
    void when_my_creator_is_requested_only_approved_active_creator_is_returned() {
        User admin = saveUser("my-admin@example.com", "my-admin", "01040000000",
                UserRole.MASTER);
        Creator active = saveApprovedCreator(
                admin.getId(), "내활성상점", "8111111111", false, false);
        Creator deletedCreator = saveApprovedCreator(
                admin.getId(), "내삭제상점", "8222222222", true, false);
        Creator deletedUserCreator = saveApprovedCreator(
                admin.getId(), "내탈퇴상점", "8333333333", false, true);
        Creator pending = savePendingCreator("내대기상점", "8444444444");
        Creator rejected = saveRejectedCreator(
                admin.getId(), "내거절상점", "8555555555");

        var response = creatorQueryService.getMyCreator(active.getUserId());

        assertThat(response.creatorId()).isEqualTo(active.getId());
        assertThat(response.businessRegistrationNumber()).isEqualTo("8111111111");
        assertMyCreatorNotFound(deletedCreator.getUserId());
        assertMyCreatorNotFound(deletedUserCreator.getUserId());
        assertMyCreatorNotFound(pending.getUserId());
        assertMyCreatorNotFound(rejected.getUserId());
    }

    private Creator saveApprovedCreator(
            UUID adminId,
            String creatorName,
            String businessNumber,
            boolean deleteCreator,
            boolean deleteUser) {
        User user = saveUser(
                creatorName + "@example.com",
                creatorName,
                "010" + businessNumber.substring(0, 8),
                UserRole.CREATOR);
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), user.getId(), creatorName, businessNumber, CREATED_AT);
        creator.approve(adminId, CREATED_AT.plusSeconds(10));
        if (deleteCreator) {
            creator.softDelete(user.getId(), CREATED_AT.plusSeconds(20));
        }
        creatorRepository.save(creator);
        creatorRepository.flush();
        if (deleteUser) {
            user.softDelete(user.getId(), CREATED_AT.plusSeconds(30));
            userRepository.flush();
        }
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

    private Creator saveRejectedCreator(
            UUID adminId, String creatorName, String businessNumber) {
        Creator creator = savePendingCreator(creatorName, businessNumber);
        creator.reject(adminId, CREATED_AT.plusSeconds(10));
        creatorRepository.flush();
        return creator;
    }

    private void assertMyCreatorNotFound(UUID userId) {
        assertThatThrownBy(() -> creatorQueryService.getMyCreator(userId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CreatorErrorCode.CREATOR_NOT_FOUND));
    }

    private User saveUser(String email, String nickname, String phone, UserRole role) {
        User user = User.create(
                uuidGenerator.generate(), email, "encoded-password", nickname, phone,
                "서울시 예시구", null, role, CREATED_AT);
        userRepository.save(user);
        userRepository.flush();
        return user;
    }
}
