package com.sub9.userservice.creator.presentation.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.config.SecurityConfig;
import com.sub9.userservice.creator.application.service.InternalCreatorQueryService;
import com.sub9.userservice.creator.presentation.response.CreatorNameResponse;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(InternalCreatorQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("내부 창작자 상호명 조회 API")
class InternalCreatorQueryControllerTest {

    private static final UUID CREATOR_ID =
            UUID.fromString("01994c9d-32c0-7000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private InternalCreatorQueryService internalCreatorQueryService;

    @Test
    @DisplayName("인증 헤더 없이 창작자 상호명 목록을 직접 반환한다")
    void when_valid_ids_are_requested_creator_names_are_returned_without_authentication() throws Exception {
        when(internalCreatorQueryService.getCreatorNames(List.of(CREATOR_ID)))
                .thenReturn(List.of(new CreatorNameResponse(CREATOR_ID, "창작상점")));

        mockMvc.perform(post("/internal/v1/creators/names")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(CREATOR_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$[0].creatorName").value("창작상점"));
    }

    @Test
    @DisplayName("빈 목록은 빈 배열을 반환한다")
    void when_ids_are_empty_empty_array_is_returned() throws Exception {
        when(internalCreatorQueryService.getCreatorNames(List.of())).thenReturn(List.of());

        mockMvc.perform(post("/internal/v1/creators/names")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("null ID가 포함되면 400을 반환한다")
    void when_null_id_is_included_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/internal/v1/creators/names")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[null]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    @Test
    @DisplayName("ID가 50개를 초과하면 400을 반환한다")
    void when_more_than_fifty_ids_are_requested_bad_request_is_returned() throws Exception {
        List<UUID> ids = IntStream.range(0, 51).mapToObj(index -> UUID.randomUUID()).toList();

        mockMvc.perform(post("/internal/v1/creators/names")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    @Test
    @DisplayName("UUID 형식이 잘못되면 400을 반환한다")
    void when_uuid_format_is_invalid_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/internal/v1/creators/names")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"invalid-uuid\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }
}
