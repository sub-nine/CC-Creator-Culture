package com.sub9.productservice.category.presentation.command.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryHashtagJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
import com.sub9.productservice.category.presentation.command.dto.CreateCategoryRequest;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AdminCategoryCommandController - 통합 테스트")
class AdminCategoryCommandControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private HashtagJpaRepository hashtagJpaRepository;

    @Autowired
    private CategoryHashtagJpaRepository categoryHashtagJpaRepository;

    private final UUID adminUserId = UUID.randomUUID();

    @Nested
    @DisplayName("카테고리 관리")
    class CategoryManagement {

        @Test
        @DisplayName("관리자가 임의로 신규 카테고리를 추가한다")
        void createCategory_success() throws Exception {
            // Given
            CreateCategoryRequest request = new CreateCategoryRequest("의류", "의류 관련 카테고리");

            // When
            String responseJson = mockMvc.perform(post("/api/v1/admin/categories")
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MASTER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("카테고리 생성 성공"))
                    .andExpect(jsonPath("$.data").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            // Then
            UUID categoryId = UUID.fromString(objectMapper.readTree(responseJson).get("data").asText());
            Category category = categoryJpaRepository.findById(categoryId).orElseThrow();
            assertThat(category.getName()).isEqualTo("의류");
            assertThat(category.getDescription()).isEqualTo("의류 관련 카테고리");
        }

        @Test
        @DisplayName("카테고리 이름이 비어있으면 검증 오류로 400을 반환한다")
        void createCategory_blankName_returnsValidationError() throws Exception {
            // Given
            CreateCategoryRequest request = new CreateCategoryRequest(" ", "설명");

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories")
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MASTER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
        }
    }

    @Nested
    @DisplayName("카테고리-해시태그 연결 관리")
    class CategoryHashtagLinkManagement {

        @Test
        @DisplayName("관리자가 임의로 카테고리에 해시태그를 연결한다")
        void linkHashtag_success() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", null));
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿"));

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories/{categoryId}/hashtags/{hashtagId}",
                            category.getId(), hashtag.getId())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MASTER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("카테고리-해시태그 연결 성공"));

            CategoryHashtag categoryHashtag = categoryHashtagJpaRepository
                    .findByCategory_IdAndHashtag_IdAndDeletedAtIsNull(category.getId(), hashtag.getId())
                    .orElseThrow();
            assertThat(categoryHashtag.getMatchType()).isEqualTo(CategoryHashtagMatchType.MANUAL);
            assertThat(categoryHashtag.getStatus()).isEqualTo(CategoryHashtagStatus.MERGED);

            Hashtag persistedHashtag = hashtagJpaRepository.findById(hashtag.getId()).orElseThrow();
            assertThat(persistedHashtag.getUsageCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리에 연결을 시도하면 404를 반환한다")
        void linkHashtag_categoryNotFound_returns404() throws Exception {
            // Given
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿"));

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories/{categoryId}/hashtags/{hashtagId}",
                            UUID.randomUUID(), hashtag.getId())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MASTER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_0001"));
        }

        @Test
        @DisplayName("관리자가 카테고리에 연결된 해시태그를 연결 해제한다")
        void unlinkHashtag_success() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", null));
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿"));
            hashtag.increaseUsageCount();
            hashtagJpaRepository.save(hashtag);
            categoryHashtagJpaRepository.save(category.linkManually(hashtag, 0.0));

            // When & Then
            mockMvc.perform(delete("/api/v1/admin/categories/{categoryId}/hashtags/{hashtagId}",
                            category.getId(), hashtag.getId())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MANAGER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("카테고리-해시태그 연결 해제 성공"));

            assertThat(categoryHashtagJpaRepository
                    .findByCategory_IdAndHashtag_IdAndDeletedAtIsNull(category.getId(), hashtag.getId()))
                    .isEmpty();

            Hashtag persistedHashtag = hashtagJpaRepository.findById(hashtag.getId()).orElseThrow();
            assertThat(persistedHashtag.getUsageCount()).isZero();
        }

        @Test
        @DisplayName("연결되어 있지 않은 카테고리-해시태그를 해제하려 하면 404를 반환한다")
        void unlinkHashtag_notLinked_returns404() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", null));
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿"));

            // When & Then
            mockMvc.perform(delete("/api/v1/admin/categories/{categoryId}/hashtags/{hashtagId}",
                            category.getId(), hashtag.getId())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MANAGER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_0003"));
        }
    }

    @Nested
    @DisplayName("모호한 해시태그 연결 승인/반려 (Merge Request)")
    class MergeRequestManagement {

        @Test
        @DisplayName("관리자가 승인 대기 중인 모호한 해시태그-카테고리 연결 항목을 승인한다")
        void approveMergeRequest_success() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", null));
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿패션"));
            CategoryHashtag categoryHashtag = categoryHashtagJpaRepository.save(
                    CategoryHashtag.create(category, hashtag, CategoryHashtagMatchType.ALGORITHM,
                            CategoryHashtagStatus.PENDING_APPROVAL, 0.75));

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories/merge-requests/{requestId}/approve",
                            categoryHashtag.getId())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MANAGER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("해시태그 연결 승인 성공"));

            CategoryHashtag persisted = categoryHashtagJpaRepository.findById(categoryHashtag.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(CategoryHashtagStatus.MERGED);

            Hashtag persistedHashtag = hashtagJpaRepository.findById(hashtag.getId()).orElseThrow();
            assertThat(persistedHashtag.getUsageCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("승인 대기 상태가 아닌 항목을 승인하려 하면 400을 반환한다")
        void approveMergeRequest_notPendingApproval_returns400() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", null));
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿패션"));
            CategoryHashtag categoryHashtag = categoryHashtagJpaRepository.save(
                    CategoryHashtag.create(category, hashtag, CategoryHashtagMatchType.ALGORITHM,
                            CategoryHashtagStatus.MERGED, 0.9));

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories/merge-requests/{requestId}/approve",
                            categoryHashtag.getId())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MANAGER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_0006"));
        }

        @Test
        @DisplayName("관리자가 승인 대기 중인 모호한 해시태그-카테고리 연결 항목을 반려한다")
        void rejectMergeRequest_success() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", null));
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿패션"));
            CategoryHashtag categoryHashtag = categoryHashtagJpaRepository.save(
                    CategoryHashtag.create(category, hashtag, CategoryHashtagMatchType.ALGORITHM,
                            CategoryHashtagStatus.PENDING_APPROVAL, 0.72));

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories/merge-requests/{requestId}/reject",
                            categoryHashtag.getId())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MANAGER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("해시태그 연결 반려 성공"));

            CategoryHashtag persisted = categoryHashtagJpaRepository.findById(categoryHashtag.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(CategoryHashtagStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("해시태그 관리")
    class HashtagManagement {

        @Test
        @DisplayName("관리자가 등록되어 있는 해시태그를 임의로 삭제한다")
        void deleteHashtag_success() throws Exception {
            // Given
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿"));

            // When & Then
            mockMvc.perform(delete("/api/v1/hashtags/{hashtagId}", hashtag.getId())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MANAGER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("해시태그 삭제 성공"));

            Hashtag persisted = hashtagJpaRepository.findById(hashtag.getId()).orElseThrow();
            assertThat(persisted.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 해시태그를 삭제하려 하면 404를 반환한다")
        void deleteHashtag_notFound_returns404() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/hashtags/{hashtagId}", UUID.randomUUID())
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MANAGER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_0002"));
        }
    }
}
