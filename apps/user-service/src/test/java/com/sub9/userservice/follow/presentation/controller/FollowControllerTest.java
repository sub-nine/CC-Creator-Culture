package com.sub9.userservice.follow.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.config.SecurityConfig;
import com.sub9.userservice.follow.application.service.FollowService;
import com.sub9.userservice.follow.domain.exception.FollowErrorCode;
import com.sub9.userservice.follow.presentation.response.FollowStatusResponse;
import com.sub9.userservice.follow.presentation.response.FollowPageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(FollowController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("팔로우·언팔로우 API")
class FollowControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID CREATOR_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final UUID TOKEN_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

    @Test
    @DisplayName("CUSTOMER가 승인된 Creator를 팔로우하면 201과 활성 상태를 반환한다")
    void when_customer_follows_approved_creator_created_response_is_returned() throws Exception {
        when(followService.follow(USER_ID, CREATOR_ID))
                .thenReturn(FollowStatusResponse.followed(CREATOR_ID));

        mockMvc.perform(withGatewayHeaders(post(followPath()), "CUSTOMER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("크리에이터를 팔로우했습니다."))
                .andExpect(jsonPath("$.data.creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.following").value(true));
    }

    @Test
    @DisplayName("CUSTOMER가 활성 관계를 언팔로우하면 204를 반환한다")
    void when_customer_unfollows_active_creator_no_content_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(delete(followPath()), "CUSTOMER"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(followService).unfollow(USER_ID, CREATOR_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATOR", "MANAGER", "MASTER"})
    @DisplayName("CUSTOMER가 아닌 사용자가 팔로우를 요청하면 403을 반환한다")
    void when_non_customer_follows_creator_forbidden_response_is_returned(String role)
            throws Exception {
        mockMvc.perform(withGatewayHeaders(post(followPath()), role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
    }

    @Test
    @DisplayName("인증 헤더 없이 팔로우를 요청하면 401을 반환한다")
    void when_unauthenticated_user_follows_creator_unauthorized_response_is_returned()
            throws Exception {
        mockMvc.perform(post(followPath()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
    }

    @Test
    @DisplayName("팔로우 가능한 Creator가 없으면 404를 반환한다")
    void when_follow_target_does_not_exist_not_found_response_is_returned() throws Exception {
        when(followService.follow(any(), any()))
                .thenThrow(new BusinessException(FollowErrorCode.FOLLOW_TARGET_NOT_FOUND));

        mockMvc.perform(withGatewayHeaders(post(followPath()), "CUSTOMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FOLLOW_0001"));
    }

    @Test
    @DisplayName("이미 팔로우 중인 Creator를 다시 팔로우하면 409를 반환한다")
    void when_active_follow_exists_follow_returns_conflict() throws Exception {
        when(followService.follow(any(), any()))
                .thenThrow(new BusinessException(FollowErrorCode.FOLLOW_ALREADY_EXISTS));

        mockMvc.perform(withGatewayHeaders(post(followPath()), "CUSTOMER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FOLLOW_0002"));
    }

    @Test
    @DisplayName("creatorId 형식이 잘못되면 400을 반환한다")
    void when_creator_id_is_invalid_follow_returns_bad_request() throws Exception {
        mockMvc.perform(withGatewayHeaders(
                        post("/api/v1/follows/not-a-uuid"), "CUSTOMER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    @Test
    @DisplayName("CUSTOMER가 팔로우 여부를 조회하면 현재 상태를 반환한다")
    void when_customer_reads_follow_status_current_status_is_returned() throws Exception {
        when(followService.getFollowStatus(USER_ID, CREATOR_ID))
                .thenReturn(new FollowStatusResponse(CREATOR_ID, true));

        mockMvc.perform(withGatewayHeaders(get(followPath()), "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.following").value(true));
    }

    @Test
    @DisplayName("CUSTOMER가 팔로우 목록을 조회하면 기본 페이지 값과 목록을 반환한다")
    void when_customer_reads_follows_default_page_response_is_returned() throws Exception {
        Instant followedAt = Instant.parse("2026-09-10T07:00:00Z");
        FollowPageResponse response = new FollowPageResponse(
                List.of(new FollowPageResponse.Item(CREATOR_ID, "트렌드샵", followedAt)),
                0, 20, 1, 1, true, true);
        when(followService.getFollowedCreators(USER_ID, 0, 20)).thenReturn(response);

        mockMvc.perform(withGatewayHeaders(get("/api/v1/follows"), "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].creatorName").value("트렌드샵"))
                .andExpect(jsonPath("$.data.content[0].followedAt").value(followedAt.toString()))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATOR", "MANAGER", "MASTER"})
    @DisplayName("CUSTOMER가 아닌 사용자가 팔로우 목록을 조회하면 403을 반환한다")
    void when_non_customer_reads_follows_forbidden_response_is_returned(String role)
            throws Exception {
        mockMvc.perform(withGatewayHeaders(get("/api/v1/follows"), role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
    }

    @Test
    @DisplayName("음수 페이지로 팔로우 목록을 조회하면 400을 반환한다")
    void when_page_is_negative_follow_list_returns_bad_request() throws Exception {
        mockMvc.perform(withGatewayHeaders(get("/api/v1/follows?page=-1"), "CUSTOMER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("페이지 크기가 최대값을 초과하면 400을 반환한다")
    void when_size_exceeds_maximum_follow_list_returns_bad_request() throws Exception {
        mockMvc.perform(withGatewayHeaders(get("/api/v1/follows?size=101"), "CUSTOMER"))
                .andExpect(status().isBadRequest());
    }

    private String followPath() {
        return "/api/v1/follows/" + CREATOR_ID;
    }

    private MockHttpServletRequestBuilder withGatewayHeaders(
            MockHttpServletRequestBuilder request, String role) {
        return request
                .header("X-User-Id", USER_ID)
                .header("X-User-Role", role)
                .header("X-Token-Id", TOKEN_ID)
                .header("X-Token-Expires-At", "4102444800");
    }
}
