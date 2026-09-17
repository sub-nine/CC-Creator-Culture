package com.sub9.userservice.creator.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.userservice.config.SecurityConfig;
import com.sub9.userservice.creator.application.service.CreatorProfileService;
import com.sub9.userservice.creator.application.service.CreatorQueryService;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.presentation.response.CreatorPageResponse;
import com.sub9.userservice.creator.presentation.response.CreatorSummaryResponse;
import com.sub9.userservice.creator.presentation.response.FollowerCountResponse;
import com.sub9.userservice.creator.presentation.response.MyCreatorResponse;
import com.sub9.userservice.creator.presentation.request.UpdateCreatorRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(CreatorController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("창작자 목록·단건 조회 API")
class CreatorControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID CREATOR_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final UUID TOKEN_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatorQueryService creatorQueryService;

    @MockitoBean
    private CreatorProfileService creatorProfileService;

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "CREATOR", "MANAGER", "MASTER"})
    @DisplayName("인증된 사용자는 창작자 목록을 조회할 수 있다")
    void when_authenticated_user_reads_creators_page_is_returned(String role) throws Exception {
        CreatorPageResponse response = new CreatorPageResponse(
                List.of(new CreatorSummaryResponse(CREATOR_ID, "트렌드샵")),
                0, 20, 1, 1, true, true);
        when(creatorQueryService.getCreators(0, 20, null, "creatorName,asc"))
                .thenReturn(response);

        mockMvc.perform(withGatewayHeaders(get("/api/v1/creators"), role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("창작자 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.content[0].creatorId")
                        .value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].creatorName").value("트렌드샵"))
                .andExpect(jsonPath("$.data.content[0].businessRegistrationNumber")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("인증 헤더 없이 창작자 목록을 조회하면 401을 반환한다")
    void when_unauthenticated_user_reads_creators_unauthorized_is_returned() throws Exception {
        mockMvc.perform(get("/api/v1/creators"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
    }

    @Test
    @DisplayName("목록 조회 조건이 범위를 벗어나면 400을 반환한다")
    void when_creator_page_condition_is_invalid_bad_request_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(
                        get("/api/v1/creators?page=-1&size=101"), "CUSTOMER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지원하지 않는 정렬 조건이면 400을 반환한다")
    void when_sort_condition_is_not_supported_bad_request_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(
                        get("/api/v1/creators?sort=createdAt,desc"), "CUSTOMER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증된 사용자는 창작자 단건 정보를 조회할 수 있다")
    void when_authenticated_user_reads_creator_summary_is_returned() throws Exception {
        when(creatorQueryService.getCreator(CREATOR_ID))
                .thenReturn(new CreatorSummaryResponse(CREATOR_ID, "트렌드샵"));

        mockMvc.perform(withGatewayHeaders(
                        get("/api/v1/creators/{creatorId}", CREATOR_ID), "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("창작자 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.creatorName").value("트렌드샵"))
                .andExpect(jsonPath("$.data.businessRegistrationNumber").doesNotExist());
    }

    @Test
    @DisplayName("조회 가능한 창작자가 없으면 404를 반환한다")
    void when_creator_is_not_queryable_not_found_is_returned() throws Exception {
        when(creatorQueryService.getCreator(any()))
                .thenThrow(new BusinessException(CreatorErrorCode.CREATOR_NOT_FOUND));

        mockMvc.perform(withGatewayHeaders(
                        get("/api/v1/creators/{creatorId}", CREATOR_ID), "CUSTOMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CREATOR_0001"));
    }

    @Test
    @DisplayName("creatorId 형식이 잘못되면 400을 반환한다")
    void when_creator_id_is_invalid_bad_request_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(
                        get("/api/v1/creators/not-a-uuid"), "CUSTOMER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    @Test
    @DisplayName("CREATOR는 자신의 창작자 정보를 조회할 수 있다")
    void when_creator_reads_my_creator_private_information_is_returned() throws Exception {
        when(creatorQueryService.getMyCreator(USER_ID))
                .thenReturn(new MyCreatorResponse(CREATOR_ID, "트렌드샵", "1234567890"));

        mockMvc.perform(withGatewayHeaders(get("/api/v1/creators/me"), "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 창작자 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.creatorName").value("트렌드샵"))
                .andExpect(jsonPath("$.data.businessRegistrationNumber")
                        .value("1234567890"))
                .andExpect(jsonPath("$.data.approvedBy").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "MASTER"})
    @DisplayName("CREATOR가 아닌 사용자는 내 창작자 정보를 조회할 수 없다")
    void when_non_creator_reads_my_creator_forbidden_is_returned(String role) throws Exception {
        mockMvc.perform(withGatewayHeaders(get("/api/v1/creators/me"), role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
    }

    @Test
    @DisplayName("인증 헤더 없이 내 창작자 정보를 조회하면 401을 반환한다")
    void when_unauthenticated_user_reads_my_creator_unauthorized_is_returned() throws Exception {
        mockMvc.perform(get("/api/v1/creators/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
    }

    @Test
    @DisplayName("조회 가능한 내 창작자 정보가 없으면 404를 반환한다")
    void when_my_creator_is_not_queryable_not_found_is_returned() throws Exception {
        when(creatorQueryService.getMyCreator(USER_ID))
                .thenThrow(new BusinessException(CreatorErrorCode.CREATOR_NOT_FOUND));

        mockMvc.perform(withGatewayHeaders(get("/api/v1/creators/me"), "CREATOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CREATOR_0001"));
    }

    @Test
    @DisplayName("CREATOR는 자신의 활성 팔로워 수를 조회할 수 있다")
    void when_creator_reads_my_follower_count_count_is_returned() throws Exception {
        when(creatorQueryService.getMyFollowerCount(USER_ID))
                .thenReturn(new FollowerCountResponse(CREATOR_ID, 12L));

        mockMvc.perform(withGatewayHeaders(
                        get("/api/v1/creators/me/follower-count"), "CREATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 팔로워 수를 조회했습니다."))
                .andExpect(jsonPath("$.data.creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.followerCount").value(12));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "MASTER"})
    @DisplayName("CREATOR가 아닌 사용자는 내 팔로워 수를 조회할 수 없다")
    void when_non_creator_reads_my_follower_count_forbidden_is_returned(String role)
            throws Exception {
        mockMvc.perform(withGatewayHeaders(
                        get("/api/v1/creators/me/follower-count"), role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
    }

    @Test
    @DisplayName("인증 헤더 없이 내 팔로워 수를 조회하면 401을 반환한다")
    void when_unauthenticated_user_reads_my_follower_count_unauthorized_is_returned()
            throws Exception {
        mockMvc.perform(get("/api/v1/creators/me/follower-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
    }

    @Test
    @DisplayName("조회 가능한 내 창작자가 없으면 팔로워 수 조회는 404를 반환한다")
    void when_my_creator_for_follower_count_is_not_queryable_not_found_is_returned()
            throws Exception {
        when(creatorQueryService.getMyFollowerCount(USER_ID))
                .thenThrow(new BusinessException(CreatorErrorCode.CREATOR_NOT_FOUND));

        mockMvc.perform(withGatewayHeaders(
                        get("/api/v1/creators/me/follower-count"), "CREATOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CREATOR_0001"));
    }

    @Test
    @DisplayName("CREATOR는 자신의 창작자 정보를 수정할 수 있다")
    void when_creator_updates_my_creator_updated_information_is_returned() throws Exception {
        when(creatorProfileService.updateMyCreator(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                any(UpdateCreatorRequest.class)))
                .thenReturn(new MyCreatorResponse(CREATOR_ID, "변경상점", "9876543210"));

        mockMvc.perform(withGatewayHeaders(patch("/api/v1/creators/me"), "CREATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "creatorName": "  변경상점  ",
                                  "businessRegistrationNumber": "987-65-43210"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 창작자 정보를 수정했습니다."))
                .andExpect(jsonPath("$.data.creatorName").value("변경상점"))
                .andExpect(jsonPath("$.data.businessRegistrationNumber")
                        .value("9876543210"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "MASTER"})
    @DisplayName("CREATOR가 아닌 사용자는 내 창작자 정보를 수정할 수 없다")
    void when_non_creator_updates_my_creator_forbidden_is_returned(String role) throws Exception {
        mockMvc.perform(withGatewayHeaders(patch("/api/v1/creators/me"), role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorName\":\"변경상점\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
    }

    @Test
    @DisplayName("수정할 창작자 정보가 없으면 400을 반환한다")
    void when_update_request_is_empty_bad_request_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(patch("/api/v1/creators/me"), "CREATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("명시적으로 빈 창작자 정보를 전달하면 400을 반환한다")
    void when_update_value_is_blank_bad_request_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(patch("/api/v1/creators/me"), "CREATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorName\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사업자등록번호에 숫자와 하이픈 외 문자가 있으면 400을 반환한다")
    void when_business_number_contains_letters_bad_request_is_returned() throws Exception {
        mockMvc.perform(withGatewayHeaders(patch("/api/v1/creators/me"), "CREATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessRegistrationNumber\":\"123-AB-45678\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 헤더 없이 내 창작자 정보를 수정하면 401을 반환한다")
    void when_unauthenticated_user_updates_my_creator_unauthorized_is_returned()
            throws Exception {
        mockMvc.perform(patch("/api/v1/creators/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorName\":\"변경상점\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
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
