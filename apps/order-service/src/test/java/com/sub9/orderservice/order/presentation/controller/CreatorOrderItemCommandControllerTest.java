package com.sub9.orderservice.order.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.common.security.GatewayHeaderAuthenticationFilter;
import com.sub9.orderservice.config.SecurityConfig;
import com.sub9.orderservice.order.application.service.OrderItemStatusService;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemDetail;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(CreatorOrderItemCommandController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("창작자 주문 상품 상태 변경 API")
class CreatorOrderItemCommandControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final UUID ORDER_ITEM_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000003");
    private static final UUID PRODUCT_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000004");
    private static final UUID SKU_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000005");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderItemStatusService orderItemStatusService;

    @Test
    @DisplayName("유효한 창작자 요청은 주문 상품 상태를 변경하고 현재 상세를 반환한다")
    void when_creator_requests_valid_status_current_detail_is_returned() throws Exception {
        when(orderItemStatusService.update(USER_ID, ORDER_ITEM_ID, OrderItemStatus.PREPARING))
                .thenReturn(detail());

        mockMvc.perform(authenticatedPatch(GatewayAuthenticationPrincipal.Role.CREATOR)
                        .content("""
                                {"status":"PREPARING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("주문 상품 상태 변경 성공"))
                .andExpect(jsonPath("$.data.orderItemId").value(ORDER_ITEM_ID.toString()))
                .andExpect(jsonPath("$.data.orderStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$..shippingAddress").doesNotExist());

        verify(orderItemStatusService).update(USER_ID, ORDER_ITEM_ID, OrderItemStatus.PREPARING);
    }

    @Test
    @DisplayName("인증 헤더가 없으면 인증 오류를 반환한다")
    void when_authentication_headers_are_missing_unauthorized_is_returned() throws Exception {
        mockMvc.perform(patch(path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.UNAUTHORIZED.code()));

        verify(orderItemStatusService, never()).update(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = GatewayAuthenticationPrincipal.Role.class, names = "CREATOR", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("창작자가 아닌 역할은 상태 변경을 거부한다")
    void when_role_is_not_creator_forbidden_is_returned(GatewayAuthenticationPrincipal.Role role)
            throws Exception {
        mockMvc.perform(authenticatedPatch(role)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.FORBIDDEN.code()));

        verify(orderItemStatusService, never()).update(any(), any(), any());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidRequests")
    @DisplayName("주문 상품 상태가 없거나 올바르지 않으면 입력 오류를 반환한다")
    void when_status_is_invalid_bad_request_is_returned(
            String context, String request, String errorCode) throws Exception {
        mockMvc.perform(authenticatedPatch(GatewayAuthenticationPrincipal.Role.CREATOR)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(errorCode));

        verify(orderItemStatusService, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("주문 상품 ID 형식이 잘못되면 잘못된 요청 오류를 반환한다")
    void when_order_item_id_is_invalid_bad_request_is_returned() throws Exception {
        mockMvc.perform(authenticatedPatch(
                        GatewayAuthenticationPrincipal.Role.CREATOR,
                        "/api/v1/creator/order-items/not-a-uuid/status")
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.BAD_REQUEST.code()));

        verify(orderItemStatusService, never()).update(any(), any(), any());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("businessErrors")
    @DisplayName("주문 상품 상태 변경 오류를 응답 코드와 HTTP 상태로 반환한다")
    void when_service_rejects_status_change_business_error_is_returned(
            OrderErrorCode errorCode) throws Exception {
        when(orderItemStatusService.update(USER_ID, ORDER_ITEM_ID, OrderItemStatus.PREPARING))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticatedPatch(GatewayAuthenticationPrincipal.Role.CREATOR)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().is(errorCode.status().value()))
                .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }

    private MockHttpServletRequestBuilder authenticatedPatch(
            GatewayAuthenticationPrincipal.Role role) {
        return authenticatedPatch(role, path());
    }

    private MockHttpServletRequestBuilder authenticatedPatch(
            GatewayAuthenticationPrincipal.Role role, String path) {
        return patch(path)
                .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, USER_ID)
                .header(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, role.name())
                .header(GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER, TOKEN_ID)
                .header(GatewayHeaderAuthenticationFilter.TOKEN_EXPIRES_AT_HEADER, 1_788_400_000L)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(
                        "상태 필드 누락",
                        "{}",
                        CommonErrorCode.VALIDATION_ERROR.code()),
                Arguments.of(
                        "상태가 null",
                        "{\"status\":null}",
                        CommonErrorCode.VALIDATION_ERROR.code()),
                Arguments.of(
                        "정의되지 않은 상태",
                        "{\"status\":\"UNKNOWN\"}",
                        CommonErrorCode.BAD_REQUEST.code()));
    }

    private static Stream<Arguments> businessErrors() {
        return Stream.of(
                Arguments.of(OrderErrorCode.ORDER_NOT_FOUND),
                Arguments.of(OrderErrorCode.ORDER_ACCESS_DENIED),
                Arguments.of(OrderErrorCode.INVALID_ORDER_STATUS),
                Arguments.of(OrderErrorCode.INVALID_ORDER_ITEM_STATUS_TRANSITION));
    }

    private static String path() {
        return "/api/v1/creator/order-items/" + ORDER_ITEM_ID + "/status";
    }

    private static CreatorOrderItemDetail detail() {
        return new CreatorOrderItemDetail(
                ORDER_ITEM_ID,
                "ORD-01990a00-0000-7000-8000-000000000006",
                OrderStatus.PROCESSING,
                PRODUCT_ID,
                SKU_ID,
                "아크릴 스탠드",
                "A 타입",
                18_000,
                2,
                36_000,
                1_800,
                34_200,
                OrderItemStatus.PREPARING,
                Instant.parse("2026-09-04T00:00:00Z"));
    }
}
