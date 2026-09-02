package com.sub9.productservice.category.presentation.query.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CategoryQueryController MockMvc 테스트")
@Disabled
class CategoryQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("카테고리 조회")
    class CategoryQuery {

        @Test
        @DisplayName("키워드로 카테고리 목록을 검색한다")
        void searchCategories_success() throws Exception {
            // Given
            String keyword = "패션";

            // When & Then
            mockMvc.perform(get("/api/v1/categories")
                            .param("keyword", keyword)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("카테고리 ID로 카테고리 단건 상세 정보를 조회한다")
        void getCategory_success() throws Exception {
            // Given
            UUID categoryId = UUID.randomUUID();

            // When & Then
            mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data.categoryId").value(categoryId.toString()));
        }

        @Test
        @DisplayName("카테고리 소속 해시태그 페이징 목록을 조회한다")
        void getCategoryHashtags_success() throws Exception {
            // Given
            UUID categoryId = UUID.randomUUID();

            // When & Then
            mockMvc.perform(get("/api/v1/categories/{categoryId}/hashtags", categoryId)
                            .param("page", "0")
                            .param("size", "10")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    @Nested
    @DisplayName("관리자 승인 대기 목록 조회")
    class MergeRequestQuery {

        @Test
        @DisplayName("관리자가 모호한 해시태그 연결 승인 대기 목록을 조회한다")
        void getMergeRequests_success() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/admin/categories/merge-requests")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("해시태그 조회")
    class HashtagQuery {

        @Test
        @DisplayName("키워드로 기존 등록된 해시태그 목록을 검색한다")
        void searchHashtags_success() throws Exception {
            // Given
            String keyword = "스트릿";

            // When & Then
            mockMvc.perform(get("/api/v1/hashtags")
                            .param("keyword", keyword)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("요청 성공"))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}