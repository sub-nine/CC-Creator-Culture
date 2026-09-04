package com.sub9.orderservice.order.presentation.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.common.security.GatewayHeaderAuthenticationFilter;
import com.sub9.orderservice.config.SecurityConfig;
import com.sub9.orderservice.order.application.service.OrderQueryService;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.AdminOrderDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.AdminOrderSummary;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorGroup;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CreatorOrderItemSummary;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CustomerOrderDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.CustomerOrderSummary;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.OrderItemDetail;
import com.sub9.orderservice.order.presentation.response.OrderQueryResponse.ShippingAddressResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(OrderQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("역할별 주문 조회 API")
class OrderQueryControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final UUID CUSTOMER_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000003");
    private static final UUID CREATOR_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000004");
    private static final UUID ORDER_ITEM_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000005");
    private static final UUID PRODUCT_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000006");
    private static final UUID SKU_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000007");
    private static final String ORDER_NUMBER =
            "ORD-01990a00-0000-7000-8000-000000000008";
    private static final Instant CREATED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-04T00:10:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    @DisplayName("소비자 주문 목록은 기본 페이지로 조회하고 배송지를 노출하지 않는다")
    void when_customer_queries_orders_default_page_and_safe_fields_are_returned() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        when(orderQueryService.getCustomerOrders(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(customerSummary()), pageable, 1));

        mockMvc.perform(authenticatedGet("/api/v1/orders", GatewayAuthenticationPrincipal.Role.CUSTOMER)
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("주문 조회 성공"))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.content[0].paymentAmount").value(34_200L))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$..shippingAddress").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].creatorGroups").doesNotExist())
                .andExpect(jsonPath("$..id").doesNotExist())
                .andExpect(jsonPath("$..version").doesNotExist())
                .andExpect(jsonPath("$..updatedAt").doesNotExist())
                .andExpect(jsonPath("$..userCouponId").doesNotExist())
                .andExpect(jsonPath("$..statusHistory").doesNotExist());

        verify(orderQueryService).getCustomerOrders(USER_ID, pageable);
    }

    @Test
    @DisplayName("소비자 주문 상세는 전체 배송지와 창작자별 상품을 반환한다")
    void when_customer_queries_order_detail_shipping_address_and_groups_are_returned() throws Exception {
        when(orderQueryService.getCustomerOrder(USER_ID, OrderNumber.from(ORDER_NUMBER)))
                .thenReturn(customerDetail());

        mockMvc.perform(authenticatedGet(
                        "/api/v1/orders/" + ORDER_NUMBER,
                        GatewayAuthenticationPrincipal.Role.CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippingAddress.recipientName").value("홍길동"))
                .andExpect(jsonPath("$.data.shippingAddress.recipientPhone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.shippingAddress.postalCode").value("06236"))
                .andExpect(jsonPath("$.data.shippingAddress.addressLine1")
                        .value("서울특별시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.data.shippingAddress.addressLine2").value("101동 1001호"))
                .andExpect(jsonPath("$.data.creatorGroups[0].creatorId").value(CREATOR_ID.toString()))
                .andExpect(jsonPath("$.data.creatorGroups[0].items[0].orderItemId")
                        .value(ORDER_ITEM_ID.toString()))
                .andExpect(jsonPath("$..userCouponId").doesNotExist())
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$..id").doesNotExist())
                .andExpect(jsonPath("$..version").doesNotExist())
                .andExpect(jsonPath("$..updatedAt").doesNotExist())
                .andExpect(jsonPath("$..statusHistory").doesNotExist());
    }

    @Test
    @DisplayName("창작자 주문 상품 목록은 최대 페이지 크기와 안전한 필드로 조회한다")
    void when_creator_queries_order_items_max_page_size_is_accepted() throws Exception {
        PageRequest pageable = PageRequest.of(2, 100);
        when(orderQueryService.getCreatorOrderItems(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(creatorSummary()), pageable, 201));

        mockMvc.perform(authenticatedGet(
                        "/api/v1/creator/order-items",
                        GatewayAuthenticationPrincipal.Role.CREATOR)
                        .param("page", "2")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderItemId").value(ORDER_ITEM_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].orderStatus").value("PAID"))
                .andExpect(jsonPath("$..shippingAddress").doesNotExist())
                .andExpect(jsonPath("$..userCouponId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].customerId").doesNotExist())
                .andExpect(jsonPath("$..id").doesNotExist())
                .andExpect(jsonPath("$..version").doesNotExist())
                .andExpect(jsonPath("$..updatedAt").doesNotExist())
                .andExpect(jsonPath("$..statusHistory").doesNotExist());

        verify(orderQueryService).getCreatorOrderItems(USER_ID, pageable);
    }

    @Test
    @DisplayName("창작자 주문 상품 상세는 본인 상품 정보만 반환한다")
    void when_creator_queries_order_item_detail_only_item_data_is_returned() throws Exception {
        when(orderQueryService.getCreatorOrderItem(USER_ID, ORDER_ITEM_ID))
                .thenReturn(creatorDetail());

        mockMvc.perform(authenticatedGet(
                        "/api/v1/creator/order-items/" + ORDER_ITEM_ID,
                        GatewayAuthenticationPrincipal.Role.CREATOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderItemId").value(ORDER_ITEM_ID.toString()))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.originalAmount").value(36_000L))
                .andExpect(jsonPath("$..shippingAddress").doesNotExist())
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$..userCouponId").doesNotExist())
                .andExpect(jsonPath("$..id").doesNotExist())
                .andExpect(jsonPath("$..version").doesNotExist())
                .andExpect(jsonPath("$..updatedAt").doesNotExist())
                .andExpect(jsonPath("$..statusHistory").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(value = GatewayAuthenticationPrincipal.Role.class, names = {"MANAGER", "MASTER"})
    @DisplayName("운영자 역할은 전체 주문 목록을 조회한다")
    void when_admin_queries_orders_all_admin_roles_are_allowed(GatewayAuthenticationPrincipal.Role role)
            throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        when(orderQueryService.getAdminOrders(pageable))
                .thenReturn(new PageImpl<>(List.of(adminSummary()), pageable, 1));

        mockMvc.perform(authenticatedGet("/api/v1/admin/orders", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$..shippingAddress").doesNotExist())
                .andExpect(jsonPath("$..userCouponId").doesNotExist())
                .andExpect(jsonPath("$..id").doesNotExist())
                .andExpect(jsonPath("$..version").doesNotExist())
                .andExpect(jsonPath("$..updatedAt").doesNotExist())
                .andExpect(jsonPath("$..statusHistory").doesNotExist());

        verify(orderQueryService).getAdminOrders(pageable);
    }

    @ParameterizedTest
    @EnumSource(value = GatewayAuthenticationPrincipal.Role.class, names = {"MANAGER", "MASTER"})
    @DisplayName("운영자 역할은 주문 상세를 배송지 없이 조회한다")
    void when_admin_queries_order_detail_all_admin_roles_are_allowed(GatewayAuthenticationPrincipal.Role role)
            throws Exception {
        when(orderQueryService.getAdminOrder(OrderNumber.from(ORDER_NUMBER)))
                .thenReturn(adminDetail());

        mockMvc.perform(authenticatedGet("/api/v1/admin/orders/" + ORDER_NUMBER, role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.data.creatorGroups[0].items[0].skuId").value(SKU_ID.toString()))
                .andExpect(jsonPath("$..shippingAddress").doesNotExist())
                .andExpect(jsonPath("$..userCouponId").doesNotExist())
                .andExpect(jsonPath("$..statusHistory").doesNotExist())
                .andExpect(jsonPath("$..id").doesNotExist())
                .andExpect(jsonPath("$..version").doesNotExist())
                .andExpect(jsonPath("$..updatedAt").doesNotExist());

        verify(orderQueryService).getAdminOrder(OrderNumber.from(ORDER_NUMBER));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("forbiddenRequests")
    @DisplayName("허용되지 않은 역할은 각 조회 경로에 접근할 수 없다")
    void when_role_is_not_allowed_for_query_path_forbidden_is_returned(
            String path,
            GatewayAuthenticationPrincipal.Role role) throws Exception {
        mockMvc.perform(authenticatedGet(path, role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.FORBIDDEN.code()));

        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("인증 헤더 없이 주문을 조회하면 인증 오류를 반환한다")
    void when_authentication_headers_are_missing_unauthorized_is_returned() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.UNAUTHORIZED.code()));

        verifyNoInteractions(orderQueryService);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidPages")
    @DisplayName("페이지 범위를 벗어나면 입력값 검증 오류를 반환한다")
    void when_page_parameters_are_out_of_range_validation_error_is_returned(
            String path,
            GatewayAuthenticationPrincipal.Role role,
            String page,
            String size,
            String invalidField) throws Exception {
        mockMvc.perform(authenticatedGet(path, role)
                        .param("page", page)
                        .param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.VALIDATION_ERROR.code()))
                .andExpect(jsonPath("$.errors[*]['" + invalidField + "']").exists());

        verifyNoInteractions(orderQueryService);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidOrderNumbers")
    @DisplayName("주문번호 형식이 잘못되면 잘못된 요청 오류를 반환한다")
    void when_order_number_format_is_invalid_bad_request_is_returned(
            String path,
            GatewayAuthenticationPrincipal.Role role) throws Exception {
        mockMvc.perform(authenticatedGet(path, role))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.BAD_REQUEST.code()));

        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("주문 상품 ID 형식이 잘못되면 잘못된 요청 오류를 반환한다")
    void when_order_item_id_format_is_invalid_bad_request_is_returned() throws Exception {
        mockMvc.perform(authenticatedGet(
                        "/api/v1/creator/order-items/not-a-uuid",
                        GatewayAuthenticationPrincipal.Role.CREATOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.BAD_REQUEST.code()));

        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("페이지 값이 숫자가 아니면 잘못된 요청 오류를 반환한다")
    void when_page_is_not_a_number_bad_request_is_returned() throws Exception {
        mockMvc.perform(authenticatedGet("/api/v1/orders", GatewayAuthenticationPrincipal.Role.CUSTOMER)
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.BAD_REQUEST.code()));

        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("주문을 찾을 수 없으면 ORDER_0001과 404를 반환한다")
    void when_order_does_not_exist_not_found_is_returned() throws Exception {
        when(orderQueryService.getCustomerOrder(USER_ID, OrderNumber.from(ORDER_NUMBER)))
                .thenThrow(new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(authenticatedGet(
                        "/api/v1/orders/" + ORDER_NUMBER,
                        GatewayAuthenticationPrincipal.Role.CUSTOMER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(OrderErrorCode.ORDER_NOT_FOUND.code()));
    }

    @Test
    @DisplayName("주문 상품 접근 권한이 없으면 ORDER_0002와 403을 반환한다")
    void when_creator_cannot_access_order_item_forbidden_is_returned() throws Exception {
        when(orderQueryService.getCreatorOrderItem(USER_ID, ORDER_ITEM_ID))
                .thenThrow(new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED));

        mockMvc.perform(authenticatedGet(
                        "/api/v1/creator/order-items/" + ORDER_ITEM_ID,
                        GatewayAuthenticationPrincipal.Role.CREATOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(OrderErrorCode.ORDER_ACCESS_DENIED.code()));
    }

    private MockHttpServletRequestBuilder authenticatedGet(
            String path,
            GatewayAuthenticationPrincipal.Role role) {
        return get(path)
                .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, USER_ID)
                .header(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, role.name())
                .header(GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER, TOKEN_ID)
                .header(GatewayHeaderAuthenticationFilter.TOKEN_EXPIRES_AT_HEADER, 1_788_400_000L);
    }

    private static Stream<Arguments> forbiddenRequests() {
        return Stream.of(
                        forbiddenRoles(
                                "/api/v1/orders",
                                GatewayAuthenticationPrincipal.Role.CUSTOMER),
                        forbiddenRoles(
                                "/api/v1/orders/" + ORDER_NUMBER,
                                GatewayAuthenticationPrincipal.Role.CUSTOMER),
                        forbiddenRoles(
                                "/api/v1/creator/order-items",
                                GatewayAuthenticationPrincipal.Role.CREATOR),
                        forbiddenRoles(
                                "/api/v1/creator/order-items/" + ORDER_ITEM_ID,
                                GatewayAuthenticationPrincipal.Role.CREATOR),
                        forbiddenRoles(
                                "/api/v1/admin/orders",
                                GatewayAuthenticationPrincipal.Role.MANAGER,
                                GatewayAuthenticationPrincipal.Role.MASTER),
                        forbiddenRoles(
                                "/api/v1/admin/orders/" + ORDER_NUMBER,
                                GatewayAuthenticationPrincipal.Role.MANAGER,
                                GatewayAuthenticationPrincipal.Role.MASTER))
                .flatMap(Function.identity());
    }

    private static Stream<Arguments> forbiddenRoles(
            String path,
            GatewayAuthenticationPrincipal.Role... allowedRoles) {
        Set<GatewayAuthenticationPrincipal.Role> allowed = Set.of(allowedRoles);
        return Stream.of(GatewayAuthenticationPrincipal.Role.values())
                .filter(role -> !allowed.contains(role))
                .map(role -> Arguments.of(path, role));
    }

    private static Stream<Arguments> invalidPages() {
        return Stream.of(
                        invalidPageParameters(
                                "/api/v1/orders",
                                GatewayAuthenticationPrincipal.Role.CUSTOMER),
                        invalidPageParameters(
                                "/api/v1/creator/order-items",
                                GatewayAuthenticationPrincipal.Role.CREATOR),
                        invalidPageParameters(
                                "/api/v1/admin/orders",
                                GatewayAuthenticationPrincipal.Role.MANAGER))
                .flatMap(Function.identity());
    }

    private static Stream<Arguments> invalidPageParameters(
            String path,
            GatewayAuthenticationPrincipal.Role role) {
        return Stream.of(
                Arguments.of(path, role, "-1", "20", "page"),
                Arguments.of(path, role, "0", "0", "size"),
                Arguments.of(path, role, "0", "101", "size"));
    }

    private static Stream<Arguments> invalidOrderNumbers() {
        return Stream.of(
                Arguments.of(
                        "/api/v1/orders/not-an-order-number",
                        GatewayAuthenticationPrincipal.Role.CUSTOMER),
                Arguments.of(
                        "/api/v1/admin/orders/not-an-order-number",
                        GatewayAuthenticationPrincipal.Role.MANAGER));
    }

    private static CustomerOrderSummary customerSummary() {
        return new CustomerOrderSummary(
                ORDER_NUMBER,
                OrderStatus.PENDING_PAYMENT,
                36_000L,
                1_800L,
                34_200L,
                CREATED_AT,
                EXPIRES_AT);
    }

    private static CustomerOrderDetail customerDetail() {
        return new CustomerOrderDetail(
                ORDER_NUMBER,
                OrderStatus.PROCESSING,
                36_000L,
                1_800L,
                34_200L,
                CREATED_AT,
                EXPIRES_AT,
                new ShippingAddressResponse(
                        "홍길동",
                        "010-1234-5678",
                        "06236",
                        "서울특별시 강남구 테헤란로 1",
                        "101동 1001호"),
                List.of(creatorGroup()));
    }

    private static CreatorOrderItemSummary creatorSummary() {
        return new CreatorOrderItemSummary(
                ORDER_ITEM_ID,
                ORDER_NUMBER,
                OrderStatus.PAID,
                "아크릴 스탠드",
                "A 타입",
                2,
                34_200L,
                OrderItemStatus.ORDERED,
                CREATED_AT);
    }

    private static CreatorOrderItemDetail creatorDetail() {
        return new CreatorOrderItemDetail(
                ORDER_ITEM_ID,
                ORDER_NUMBER,
                OrderStatus.PAID,
                PRODUCT_ID,
                SKU_ID,
                "아크릴 스탠드",
                "A 타입",
                18_000L,
                2,
                36_000L,
                1_800L,
                34_200L,
                OrderItemStatus.ORDERED,
                CREATED_AT);
    }

    private static AdminOrderSummary adminSummary() {
        return new AdminOrderSummary(
                ORDER_NUMBER,
                CUSTOMER_ID,
                OrderStatus.PROCESSING,
                36_000L,
                1_800L,
                34_200L,
                CREATED_AT,
                EXPIRES_AT);
    }

    private static AdminOrderDetail adminDetail() {
        return new AdminOrderDetail(
                ORDER_NUMBER,
                CUSTOMER_ID,
                OrderStatus.PROCESSING,
                36_000L,
                1_800L,
                34_200L,
                CREATED_AT,
                EXPIRES_AT,
                List.of(creatorGroup()));
    }

    private static CreatorGroup creatorGroup() {
        return new CreatorGroup(
                CREATOR_ID,
                List.of(new OrderItemDetail(
                        ORDER_ITEM_ID,
                        PRODUCT_ID,
                        SKU_ID,
                        "아크릴 스탠드",
                        "A 타입",
                        18_000L,
                        2,
                        1_800L,
                        34_200L,
                        OrderItemStatus.ORDERED)));
    }
}
