package com.sub9.orderservice.order.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.application.port.output.StockOperationUncertainException;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ProductStockAdapterTest {
    private final UUID orderId = UUID.randomUUID();
    private final List<StockItem> items = List.of(new StockItem(UUID.randomUUID(), 2));
    @Mock private ProductStockClient client;
    private ProductStockAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductStockAdapter(client, JsonMapper.builder().build());
    }

    @Test
    void when_deducting_stock_request_contains_order_and_items() {
        adapter.deduct(orderId, items);
        verify(client).deduct(new ProductStockClient.DeductRequest(orderId, items));
    }

    @ParameterizedTest
    @EnumSource(RestoreReason.class)
    void when_restoring_stock_request_preserves_reason(RestoreReason reason) {
        adapter.restore(orderId, items, reason);
        verify(client).restore(new ProductStockClient.RestoreRequest(orderId, items, reason));
    }

    @ParameterizedTest
    @CsvSource({"409,STOCK_0002", "404,SKU_0004"})
    void when_product_confirms_failure_business_error_is_preserved(int status, String code) {
        doThrow(failure(status, "{\"errorCode\":\"" + code + "\"}"))
                .when(client).deduct(any());
        assertThatThrownBy(() -> adapter.deduct(orderId, items))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode().code()).isEqualTo(code);
                    assertThat(exception.getErrorCode().status().value()).isEqualTo(status);
                });
    }

    @ParameterizedTest
    @CsvSource({"500,STOCK_0002", "404,STOCK_0002", "409,SKU_0004", "409,UNKNOWN", "401,UNKNOWN"})
    void when_error_is_not_confirmed_result_remains_uncertain(int status, String code) {
        doThrow(failure(status, "{\"errorCode\":\"" + code + "\"}"))
                .when(client).deduct(any());
        assertThatThrownBy(() -> adapter.deduct(orderId, items))
                .isInstanceOf(StockOperationUncertainException.class);
    }

    @Test
    void when_error_body_is_invalid_result_remains_uncertain() {
        for (String body : List.of("", "not-json", "{}", "null", "{\"errorCode\":42}")) {
            doThrow(failure(409, body)).when(client).restore(any());
            assertThatThrownBy(() -> adapter.restore(orderId, items, RestoreReason.ORDER_CANCEL))
                    .isInstanceOf(StockOperationUncertainException.class);
        }
    }

    @Test
    void when_connection_fails_result_remains_uncertain() {
        Request request = Request.create(Request.HttpMethod.POST, "http://product/internal/v1/stocks/deduct",
                Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        doThrow(new RetryableException(-1, "연결 실패", Request.HttpMethod.POST,
                new ConnectException(), (Long) null, request)).when(client).deduct(any());
        assertThatThrownBy(() -> adapter.deduct(orderId, items))
                .isInstanceOf(StockOperationUncertainException.class)
                .hasCauseInstanceOf(RetryableException.class);
    }

    @Test
    void when_request_is_invalid_no_http_call_is_made() {
        assertThatThrownBy(() -> adapter.deduct(null, items)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> adapter.restore(orderId, items, null)).isInstanceOf(BusinessException.class);
        for (List<StockItem> invalid : Arrays.<List<StockItem>>asList(null, List.of(), Arrays.asList((StockItem) null))) {
            assertThatThrownBy(() -> adapter.deduct(orderId, invalid)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> adapter.restore(orderId, invalid, RestoreReason.ORDER_CANCEL))
                    .isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(client);
    }

    private FeignException failure(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "http://product/internal/v1/stocks/deduct",
                Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("deduct", Response.builder().request(request).status(status)
                .headers(Map.of()).body(body, StandardCharsets.UTF_8).build());
    }
}
