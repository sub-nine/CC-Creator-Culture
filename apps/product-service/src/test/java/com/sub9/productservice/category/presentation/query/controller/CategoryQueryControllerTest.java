package com.sub9.productservice.category.presentation.query.controller;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryHashtagJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("CategoryQueryController - 통합 테스트")
class CategoryQueryControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private HashtagJpaRepository hashtagJpaRepository;

    @Autowired
    private CategoryHashtagJpaRepository categoryHashtagJpaRepository;

    private final UUID adminUserId = UUID.randomUUID();

    @Nested
    @DisplayName("카테고리 조회")
    class CategoryQuery {

        @Test
        @DisplayName("키워드로 카테고리 목록을 검색한다")
        void searchCategories_success() throws Exception {
            // Given
            categoryJpaRepository.save(Category.create("패션 잡화", null));
            categoryJpaRepository.save(Category.create("전자기기", null));

            // When & Then
            mockMvc.perform(get("/api/v1/categories")
                            .param("keyword", "패션")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].name").value("패션 잡화"));
        }

        @Test
        @DisplayName("카테고리 ID로 카테고리 단건 상세 정보를 조회한다")
        void getCategory_success() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", "패션 관련 카테고리"));
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿"));
            categoryHashtagJpaRepository.save(CategoryHashtag.create(
                    category, hashtag, CategoryHashtagMatchType.MANUAL, CategoryHashtagStatus.MERGED, 0.0));

            // When & Then
            mockMvc.perform(get("/api/v1/categories/{categoryId}", category.getId())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data.id").value(category.getId().toString()))
                    .andExpect(jsonPath("$.data.name").value("패션"))
                    .andExpect(jsonPath("$.data.description").value("패션 관련 카테고리"))
                    .andExpect(jsonPath("$.data.hashtags[0].name").value("스트릿"));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리를 조회하면 404를 반환한다")
        void getCategory_notFound_returns404() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/categories/{categoryId}", UUID.randomUUID())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_0001"));
        }

        @Test
        @DisplayName("카테고리 소속 해시태그 페이징 목록을 조회한다")
        void getCategoryHashtags_success() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", null));
            Hashtag mergedHashtag = hashtagJpaRepository.save(Hashtag.create("스트릿"));
            Hashtag pendingHashtag = hashtagJpaRepository.save(Hashtag.create("빈티지"));
            categoryHashtagJpaRepository.save(CategoryHashtag.create(
                    category, mergedHashtag, CategoryHashtagMatchType.MANUAL, CategoryHashtagStatus.MERGED, 0.0));
            categoryHashtagJpaRepository.save(CategoryHashtag.create(
                    category, pendingHashtag, CategoryHashtagMatchType.ALGORITHM, CategoryHashtagStatus.PENDING_APPROVAL, 0.75));

            // When & Then
            mockMvc.perform(get("/api/v1/categories/{categoryId}/hashtags", category.getId())
                            .param("page", "0")
                            .param("size", "10")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].name").value("스트릿"));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리의 해시태그 목록을 조회하면 404를 반환한다")
        void getCategoryHashtags_categoryNotFound_returns404() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/categories/{categoryId}/hashtags", UUID.randomUUID())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_0001"));
        }
    }

    @Nested
    @DisplayName("관리자 승인 대기 목록 조회")
    class MergeRequestQuery {

        @Test
        @DisplayName("관리자가 모호한 해시태그 연결 승인 대기 목록을 조회한다")
        void getMergeRequests_success() throws Exception {
            // Given
            Category category = categoryJpaRepository.save(Category.create("패션", null));
            Hashtag hashtag = hashtagJpaRepository.save(Hashtag.create("스트릿패션"));
            categoryHashtagJpaRepository.save(CategoryHashtag.create(
                    category, hashtag, CategoryHashtagMatchType.ALGORITHM, CategoryHashtagStatus.PENDING_APPROVAL, 0.75));

            // When & Then
            mockMvc.perform(get("/api/v1/admin/categories/merge-requests")
                            .header("X-User-Id", adminUserId)
                            .header("X-USER-Role", "MANAGER")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].categoryName").value("패션"))
                    .andExpect(jsonPath("$.data.content[0].hashtagName").value("스트릿패션"))
                    .andExpect(jsonPath("$.data.content[0].status").value("PENDING_APPROVAL"));
        }
    }

    @Nested
    @DisplayName("해시태그 조회")
    class HashtagQuery {

        @Test
        @DisplayName("키워드로 기존 등록된 해시태그 목록을 검색한다")
        void searchHashtags_success() throws Exception {
            // Given
            hashtagJpaRepository.save(Hashtag.create("스트릿패션"));
            hashtagJpaRepository.save(Hashtag.create("전자기기"));

            // When & Then
            mockMvc.perform(get("/api/v1/hashtags")
                            .param("keyword", "스트릿")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].name").value("스트릿패션"));
        }
    }
}
