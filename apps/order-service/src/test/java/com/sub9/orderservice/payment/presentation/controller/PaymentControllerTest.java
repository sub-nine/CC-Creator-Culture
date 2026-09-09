package com.sub9.orderservice.payment.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal.Role;
import com.sub9.orderservice.common.security.GatewayHeaderAuthenticationFilter;
import com.sub9.orderservice.config.SecurityConfig;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.payment.application.dto.MockPaymentResult;
import com.sub9.orderservice.payment.application.service.MockPaymentService;
import com.sub9.orderservice.payment.domain.model.PaymentMethod;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("모의 결제 API")
class PaymentControllerTest {

    private static final UUID USER_ID = UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final String ORDER_NUMBER = "ORD-01990a00-0000-7000-8000-000000000003";
    private static final String PATH = "/api/v1/orders/" + ORDER_NUMBER + "/payments";
    private static final Instant PROCESSED_AT = Instant.parse("2026-09-07T00:00:30.123456Z");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MockPaymentService paymentService;

    @ParameterizedTest
    @ValueSource(longs = {34200, 0})
    @DisplayName("소비자는 멱등 키 없이 결제하고 서버의 금액과 처리 시각을 받는다")
    void when_customer_pays_response_contains_saved_payment(long amount) throws Exception {
        when(paymentService.process(USER_ID, OrderNumber.from(ORDER_NUMBER), PaymentStatus.SUCCESS))
                .thenReturn(result(PaymentStatus.SUCCESS, amount));

        mockMvc.perform(request(Role.CUSTOMER).content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("모의 결제 성공"))
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.data.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.method").value("MOCK"))
                .andExpect(jsonPath("$.data.amount").value(amount))
                .andExpect(jsonPath("$.data.processedAt").value(PROCESSED_AT.toString()));

        verify(paymentService).process(USER_ID, OrderNumber.from(ORDER_NUMBER), PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("서비스에서 실패 결과를 반환하면 공통 실패 응답으로 변환한다")
    void when_service_returns_failure_api_returns_payment_error() throws Exception {
        when(paymentService.process(USER_ID, OrderNumber.from(ORDER_NUMBER), PaymentStatus.FAILED))
                .thenReturn(result(PaymentStatus.FAILED, 34200));

        mockMvc.perform(request(Role.CUSTOMER).content("{\"result\":\"FAILED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_0001"))
                .andExpect(jsonPath("$.message").value("모의 결제에 실패했습니다."))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(paymentService).process(USER_ID, OrderNumber.from(ORDER_NUMBER), PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("인증 정보가 없으면 결제를 처리하지 않는다")
    void when_authentication_is_missing_request_is_rejected() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
        verifyNoInteractions(paymentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-User-Id", "X-User-Role"})
    @DisplayName("인증 헤더가 잘못되면 결제를 처리하지 않는다")
    void when_authentication_header_is_invalid_request_is_rejected(String header) throws Exception {
        var request = request(Role.CUSTOMER).content("{\"result\":\"SUCCESS\"}")
                .with(servletRequest -> {
                    servletRequest.removeHeader(header);
                    servletRequest.addHeader(header, "invalid");
                    return servletRequest;
                });
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0007"));
        verifyNoInteractions(paymentService);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CREATOR", "MANAGER", "MASTER"})
    @DisplayName("소비자가 아닌 역할은 결제할 수 없다")
    void when_role_is_not_customer_request_is_forbidden(Role role) throws Exception {
        mockMvc.perform(request(role).content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0008"));
        verifyNoInteractions(paymentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"result\":null}", "{\"result\":\"\"}", "{\"result\":\" \"}",
            "{\"result\":\"success\"}", "{\"result\":\"PENDING\"}", "{\"result\":\"SUCCESS \"}",
            "{\"result\":0}", "{\"result\":true}", "{\"result\":[]}", "{\"result\":{}}",
            "{\"result\":\"SUCCESS\",\"amount\":0}",
            "{\"userId\":\"other-user\",\"result\":\"SUCCESS\"}",
            "{\"result\":\"FAILED\",\"processedAt\":null}",
            "{\"result\":\"SUCCESS\",\"extra\":{}}", "{", "[]", "null", ""
    })
    @DisplayName("잘못된 본문과 추가 필드는 결제 처리 전에 거부한다")
    void when_body_is_invalid_request_returns_validation_error(String json) throws Exception {
        mockMvc.perform(request(Role.CUSTOMER).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("주문번호 형식이 잘못되면 기존 주문 API와 같은 오류를 반환한다")
    void when_order_number_is_invalid_request_returns_bad_request() throws Exception {
        mockMvc.perform(request(Role.CUSTOMER, "/api/v1/orders/invalid/payments")
                        .content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
        verifyNoInteractions(paymentService);
    }

    @ParameterizedTest
    @CsvSource({
            "ORDER_NOT_FOUND, 404, ORDER_0001", "ORDER_ACCESS_DENIED, 403, ORDER_0002",
            "INVALID_ORDER_STATUS, 409, ORDER_0003", "ORDER_ALREADY_EXPIRED, 409, ORDER_0004"
    })
    @DisplayName("주문에서 발생한 업무 오류의 상태와 코드를 유지한다")
    void when_order_rejects_payment_original_error_is_returned(OrderErrorCode error, int status, String code)
            throws Exception {
        when(paymentService.process(any(), any(), any())).thenThrow(new BusinessException(error));
        mockMvc.perform(request(Role.CUSTOMER).content("{\"result\":\"SUCCESS\"}"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.errorCode").value(code));
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    @DisplayName("같은 결제 결과의 반복 요청은 같은 응답을 반환한다")
    void when_same_result_is_repeated_response_is_unchanged(PaymentStatus result) throws Exception {
        when(paymentService.process(USER_ID, OrderNumber.from(ORDER_NUMBER), result))
                .thenReturn(result(result, 34200));
        String json = "{\"result\":\"" + result + "\"}";
        int expectedStatus = result == PaymentStatus.SUCCESS ? 200 : 400;
        String first = mockMvc.perform(request(Role.CUSTOMER).content(json))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        String repeated = mockMvc.perform(request(Role.CUSTOMER).content(json))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        assertThat(repeated).isEqualTo(first);
    }

    @Test
    @DisplayName("예상하지 못한 서비스 오류는 모의 결제 실패로 바꾸지 않는다")
    void when_service_fails_unexpectedly_api_returns_server_error() throws Exception {
        when(paymentService.process(any(), any(), any())).thenThrow(new IllegalStateException("저장 실패"));
        mockMvc.perform(request(Role.CUSTOMER).content("{\"result\":\"FAILED\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    private MockHttpServletRequestBuilder request(Role role) {
        return request(role, PATH);
    }

    private MockHttpServletRequestBuilder request(Role role, String path) {
        return post(path).contentType(MediaType.APPLICATION_JSON)
                .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, USER_ID)
                .header(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, role.name());
    }

    private MockPaymentResult result(PaymentStatus status, long amount) {
        return new MockPaymentResult(PAYMENT_ID, ORDER_NUMBER, PaymentMethod.MOCK, status, amount,
                status == PaymentStatus.FAILED ? "MOCK_PAYMENT_FAILED" : null, PROCESSED_AT);
    }
}
