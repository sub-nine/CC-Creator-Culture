package com.sub9.productservice.category.presentation.command.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub9.productservice.category.presentation.command.dto.CreateCategoryRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCategoryCommandController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminCategoryCommandController MockMvc 테스트")
@Disabled
class AdminCategoryCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("카테고리 관리")
    class CategoryManagement {

        @Test
        @DisplayName("관리자가 임의로 신규 카테고리를 추가한다")
        void createCategory_success() throws Exception {
            // Given
            CreateCategoryRequest request = new CreateCategoryRequest("의류", "의류 관련 카테고리");

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("카테고리 생성 성공"))
                    .andExpect(jsonPath("$.data").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("카테고리-해시태그 연결 관리")
    class CategoryHashtagLinkManagement {

        @Test
        @DisplayName("관리자가 임의로 카테고리에 해시태그를 연결한다")
        void linkHashtag_success() throws Exception {
            // Given
            UUID categoryId = UUID.randomUUID();
            UUID hashtagId = UUID.randomUUID();

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories/{categoryId}/hashtags/{hashtagId}", categoryId, hashtagId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("카테고리-해시태그 연결 성공"));
        }

        @Test
        @DisplayName("관리자가 카테고리에 연결된 해시태그를 연결 해제한다")
        void unlinkHashtag_success() throws Exception {
            // Given
            UUID categoryId = UUID.randomUUID();
            UUID hashtagId = UUID.randomUUID();

            // When & Then
            mockMvc.perform(delete("/api/v1/admin/categories/{categoryId}/hashtags/{hashtagId}", categoryId, hashtagId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("카테고리-해시태그 연결 해제 성공"));
        }
    }

    @Nested
    @DisplayName("모호한 해시태그 연결 승인/반려 (Merge Request)")
    class MergeRequestManagement {

        @Test
        @DisplayName("관리자가 승인 대기 중인 모호한 해시태그-카테고리 연결 항목을 승인한다")
        void approveMergeRequest_success() throws Exception {
            // Given
            UUID requestId = UUID.randomUUID();

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories/merge-requests/{requestId}/approve", requestId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("해시태그 연결 승인 성공"));
        }

        @Test
        @DisplayName("관리자가 승인 대기 중인 모호한 해시태그-카테고리 연결 항목을 반려한다")
        void rejectMergeRequest_success() throws Exception {
            // Given
            UUID requestId = UUID.randomUUID();

            // When & Then
            mockMvc.perform(post("/api/v1/admin/categories/merge-requests/{requestId}/reject", requestId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("해시태그 연결 반려 성공"));
        }
    }

    @Nested
    @DisplayName("해시태그 관리")
    class HashtagManagement {

        @Test
        @DisplayName("관리자가 등록되어 있는 해시태그를 임의로 삭제한다")
        void deleteHashtag_success() throws Exception {
            // Given
            UUID hashtagId = UUID.randomUUID();

            // When & Then
            mockMvc.perform(delete("/api/v1/hashtags/{hashtagId}", hashtagId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("해시태그 삭제 성공"));
        }
    }
}