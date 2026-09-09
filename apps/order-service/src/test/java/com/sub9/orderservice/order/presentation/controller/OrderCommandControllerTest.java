package com.sub9.orderservice.order.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.common.security.GatewayHeaderAuthenticationFilter;
import com.sub9.orderservice.config.SecurityConfig;
import com.sub9.orderservice.order.application.service.CreateOrderCommand;
import com.sub9.orderservice.order.application.service.OrderCancellationResult;
import com.sub9.orderservice.order.application.service.OrderCancellationService;
import com.sub9.orderservice.order.application.service.OrderCreationResult;
import com.sub9.orderservice.order.application.service.OrderCreationService;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.presentation.response.CreateOrderResponse;
import com.sub9.orderservice.order.presentation.response.CancelOrderResponse;
import java.time.Instant;
import java.util.List;
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

@WebMvcTest(OrderCommandController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("주문 생성과 전체 취소 API")
class OrderCommandControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID CART_ITEM_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000003");
    private static final UUID USER_COUPON_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000004");
    private static final String IDEMPOTENCY_KEY = "create-order-20260904-001";
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-04T03:10:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCreationService orderCreationService;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    @Test
    @DisplayName("본문 없이 취소하면 인증 사용자와 주문번호를 전달하고 취소 결과를 반환한다")
    void when_customer_cancels_without_body_result_is_returned() throws Exception {
        OrderNumber number = OrderNumber.issue(USER_ID);
        Instant canceledAt = Instant.parse("2026-09-06T00:00:00Z");
        when(orderCancellationService.cancel(USER_ID, IDEMPOTENCY_KEY, number))
                .thenReturn(new OrderCancellationResult(200, ApiResponse.success("주문 취소 성공",
                        new CancelOrderResponse(number.toString(), OrderStatus.CANCELED, canceledAt))));

        mockMvc.perform(cancelRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER, number.toString())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("주문 취소 성공"))
                .andExpect(jsonPath("$.data.orderNumber").value(number.toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.canceledAt").value(canceledAt.toString()));

        verify(orderCancellationService).cancel(USER_ID, IDEMPOTENCY_KEY, number);
    }

    @Test
    @DisplayName("미인증 취소 요청은 서비스 호출 없이 401을 반환한다")
    void when_cancellation_is_unauthenticated_unauthorized_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderNumber}/cancel", OrderNumber.issue(USER_ID))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.UNAUTHORIZED.code()));

        verifyNoInteractions(orderCancellationService);
    }

    @ParameterizedTest
    @EnumSource(value = GatewayAuthenticationPrincipal.Role.class, names = {"CREATOR", "MANAGER", "MASTER"})
    @DisplayName("소비자가 아닌 역할의 취소 요청은 서비스 호출 없이 403을 반환한다")
    void when_role_is_not_customer_cancellation_is_forbidden(GatewayAuthenticationPrincipal.Role role)
            throws Exception {
        mockMvc.perform(cancelRequest(role, OrderNumber.issue(USER_ID).toString())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.FORBIDDEN.code()));

        verifyNoInteractions(orderCancellationService);
    }

    @Test
    @DisplayName("취소 멱등 키 누락과 잘못된 주문번호는 서비스 호출 없이 400을 반환한다")
    void when_cancellation_key_is_missing_or_number_is_invalid_bad_request_is_returned() throws Exception {
        mockMvc.perform(cancelRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER, OrderNumber.issue(USER_ID).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.BAD_REQUEST.code()));
        mockMvc.perform(cancelRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER, "invalid-number")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.BAD_REQUEST.code()));

        verifyNoInteractions(orderCancellationService);
    }

    @ParameterizedTest
    @EnumSource(value = OrderErrorCode.class, names = {"ORDER_NOT_FOUND", "ORDER_ACCESS_DENIED",
            "INVALID_ORDER_STATUS", "CANNOT_CANCEL_ORDER_IN_PROGRESS", "IDEMPOTENCY_KEY_REUSED",
            "ORDER_REQUEST_IN_PROGRESS"})
    @DisplayName("취소 도메인 오류의 HTTP 상태와 오류 코드를 그대로 반환한다")
    void when_cancellation_fails_domain_error_is_returned(OrderErrorCode error) throws Exception {
        OrderNumber number = OrderNumber.issue(USER_ID);
        when(orderCancellationService.cancel(USER_ID, IDEMPOTENCY_KEY, number))
                .thenThrow(new BusinessException(error));

        mockMvc.perform(cancelRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER, number.toString())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().is(error.status().value()))
                .andExpect(jsonPath("$.errorCode").value(error.code()));
    }

    private MockHttpServletRequestBuilder cancelRequest(GatewayAuthenticationPrincipal.Role role, String number) {
        return post("/api/v1/orders/{orderNumber}/cancel", number)
                .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, USER_ID)
                .header(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, role.name());
    }

    @Test
    @DisplayName("유효한 CUSTOMER 요청은 주문 생성 명령을 전달하고 201을 반환한다")
    void 유효한_customer_요청일_때_주문을_생성하면_명령을_전달하고_201을_반환한다() throws Exception {
        ApiResponse<CreateOrderResponse> responseBody = ApiResponse.success(
                "주문 생성 성공",
                new CreateOrderResponse(
                        "ORD-01990a00-0000-7000-8000-000000000005",
                        OrderStatus.PENDING_PAYMENT,
                        36_000L,
                        1_800L,
                        34_200L,
                        EXPIRES_AT));
        when(orderCreationService.create(USER_ID, IDEMPOTENCY_KEY, expectedCommand()))
                .thenReturn(new OrderCreationResult(201, responseBody));

        mockMvc.perform(orderRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("주문 생성 성공"))
                .andExpect(jsonPath("$.data.orderNumber")
                        .value("ORD-01990a00-0000-7000-8000-000000000005"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.originalAmount").value(36_000L))
                .andExpect(jsonPath("$.data.discountAmount").value(1_800L))
                .andExpect(jsonPath("$.data.paymentAmount").value(34_200L))
                .andExpect(jsonPath("$.data.expiresAt").value(EXPIRES_AT.toString()));

        verify(orderCreationService).create(USER_ID, IDEMPOTENCY_KEY, expectedCommand());
    }

    @Test
    @DisplayName("인증 헤더가 없으면 COMMON_0007과 401을 반환한다")
    void 인증_헤더가_없을_때_주문을_생성하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.UNAUTHORIZED.code()));

        verify(orderCreationService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("CREATOR가 주문을 생성하면 COMMON_0008과 403을 반환한다")
    void creator가_주문을_생성할_때_권한을_확인하면_403을_반환한다() throws Exception {
        mockMvc.perform(orderRequest(GatewayAuthenticationPrincipal.Role.CREATOR)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.FORBIDDEN.code()));

        verify(orderCreationService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("Idempotency-Key가 없으면 COMMON_0002와 400을 반환한다")
    void 멱등_키가_없을_때_주문을_생성하면_400을_반환한다() throws Exception {
        mockMvc.perform(orderRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.BAD_REQUEST.code()));

        verify(orderCreationService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("중복 장바구니 항목으로 주문하면 ORDER_0009와 400을 반환한다")
    void 장바구니_항목이_중복될_때_주문을_생성하면_400을_반환한다() throws Exception {
        when(orderCreationService.create(USER_ID, IDEMPOTENCY_KEY, duplicateItemsCommand()))
                .thenThrow(new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS));

        mockMvc.perform(orderRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(duplicateItemsRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(OrderErrorCode.INVALID_ORDER_ITEMS.code()));
    }

    @ParameterizedTest(name = "[{index}] 잘못된 멱등 키")
    @MethodSource("invalidIdempotencyKeys")
    @DisplayName("멱등 키 형식이 잘못되면 COMMON_0003과 400을 반환한다")
    void 멱등_키_형식이_잘못됐을_때_주문을_생성하면_400을_반환한다(String invalidKey)
            throws Exception {
        when(orderCreationService.create(USER_ID, invalidKey, expectedCommand()))
                .thenThrow(new BusinessException(CommonErrorCode.VALIDATION_ERROR));

        mockMvc.perform(orderRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER)
                        .header("Idempotency-Key", invalidKey)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidRequests")
    @DisplayName("주문 상품이나 배송지가 유효하지 않으면 COMMON_0003과 400을 반환한다")
    void 주문_요청이_유효하지_않을_때_검증하면_400을_반환한다(
            String context, String request, String invalidField) throws Exception {
        mockMvc.perform(orderRequest(GatewayAuthenticationPrincipal.Role.CUSTOMER)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()))
                .andExpect(jsonPath("$.errors[*]['" + invalidField + "']").exists());

        verify(orderCreationService, never()).create(any(), any(), any());
    }

    private MockHttpServletRequestBuilder orderRequest(GatewayAuthenticationPrincipal.Role role) {
        return post("/api/v1/orders")
                .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, USER_ID)
                .header(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, role.name())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static CreateOrderCommand expectedCommand() {
        return new CreateOrderCommand(
                List.of(new CreateOrderCommand.Item(CART_ITEM_ID, USER_COUPON_ID)),
                new CreateOrderCommand.ShippingAddress(
                        "홍길동",
                        "010-1234-5678",
                        "06236",
                        "서울특별시 강남구 테헤란로 1",
                        "101동 1001호"));
    }

    private static CreateOrderCommand duplicateItemsCommand() {
        return new CreateOrderCommand(
                List.of(
                        new CreateOrderCommand.Item(CART_ITEM_ID, USER_COUPON_ID),
                        new CreateOrderCommand.Item(CART_ITEM_ID, null)),
                expectedCommand().shippingAddress());
    }

    private static String validRequest() {
        return """
                {
                  "items": [
                    {
                      "cartItemId": "%s",
                      "userCouponId": "%s"
                    }
                  ],
                  "shippingAddress": {
                    "recipientName": "홍길동",
                    "recipientPhone": "010-1234-5678",
                    "postalCode": "06236",
                    "addressLine1": "서울특별시 강남구 테헤란로 1",
                    "addressLine2": "101동 1001호"
                  }
                }
                """.formatted(CART_ITEM_ID, USER_COUPON_ID);
    }

    private static String duplicateItemsRequest() {
        return """
                {
                  "items": [
                    {
                      "cartItemId": "%s",
                      "userCouponId": "%s"
                    },
                    {
                      "cartItemId": "%s",
                      "userCouponId": null
                    }
                  ],
                  "shippingAddress": {
                    "recipientName": "홍길동",
                    "recipientPhone": "010-1234-5678",
                    "postalCode": "06236",
                    "addressLine1": "서울특별시 강남구 테헤란로 1",
                    "addressLine2": "101동 1001호"
                  }
                }
                """.formatted(CART_ITEM_ID, USER_COUPON_ID, CART_ITEM_ID);
    }

    private static Stream<String> invalidIdempotencyKeys() {
        return Stream.of(" ", "a".repeat(101));
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(
                        "주문 상품이 비어 있음",
                        validRequest().replace(
                                """
                                [
                                    {
                                      "cartItemId": "%s",
                                      "userCouponId": "%s"
                                    }
                                  ]
                                """.formatted(CART_ITEM_ID, USER_COUPON_ID).strip(),
                                "[]"),
                        "items"),
                Arguments.of(
                        "수령인 이름이 공백임",
                        validRequest().replace("\"recipientName\": \"홍길동\"", "\"recipientName\": \" \""),
                        "shippingAddress.recipientName"));
    }
}
