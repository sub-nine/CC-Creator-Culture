package com.sub9.orderservice.order.presentation.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.orderservice.order.application.service.OrderQueryService;
import com.sub9.orderservice.order.presentation.response.ProductPurchaseInfo;
import com.sub9.orderservice.support.AbstractControllerTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(InternalOrderQueryController.class)
@DisplayName("내부 구매 확정 조회 API")
class InternalOrderQueryControllerTest extends AbstractControllerTest {

    private static final String PATH = "/internal/v1/orders/purchase-status";
    private static final UUID USER_ID = UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("01990a00-0000-7000-8000-000000000002");
    private static final UUID PRODUCT_ID = UUID.fromString("01990a00-0000-7000-8000-000000000003");

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    @DisplayName("인증 헤더 없이 구매 확정 항목을 조회하면 래퍼 없이 상품 ID와 true를 반환한다")
    void when_item_is_purchased_product_id_and_true_are_returned() throws Exception {
        when(orderQueryService.getPurchaseStatus(USER_ID, ITEM_ID))
                .thenReturn(new ProductPurchaseInfo(PRODUCT_ID, true));

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"productId":"%s","purchased":true}
                        """.formatted(PRODUCT_ID)));
    }

    @Test
    @DisplayName("미구매이면 productId의 null 필드를 포함해 false를 반환한다")
    void when_item_is_not_purchased_explicit_null_and_false_are_returned() throws Exception {
        when(orderQueryService.getPurchaseStatus(USER_ID, ITEM_ID))
                .thenReturn(new ProductPurchaseInfo(null, false));

        mockMvc.perform(request())
                .andExpect(status().isOk())
                .andExpect(content().json("{\"productId\":null,\"purchased\":false}"));
    }

    @ParameterizedTest
    @CsvSource({"userId,", "orderItemId,", "userId,invalid", "orderItemId,invalid"})
    @DisplayName("필수 쿼리가 없거나 UUID 형식이 아니면 400을 반환한다")
    void when_query_is_invalid_bad_request_is_returned(String field, String value) throws Exception {
        var request = get(PATH);
        if (!field.equals("userId")) {
            request.param("userId", USER_ID.toString());
        }
        if (!field.equals("orderItemId")) {
            request.param("orderItemId", ITEM_ID.toString());
        }
        if (value != null) {
            request.param(field, value);
        }
        mockMvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(orderQueryService);
    }

    private MockHttpServletRequestBuilder request() {
        return get(PATH)
                .param("userId", USER_ID.toString())
                .param("orderItemId", ITEM_ID.toString());
    }
}
