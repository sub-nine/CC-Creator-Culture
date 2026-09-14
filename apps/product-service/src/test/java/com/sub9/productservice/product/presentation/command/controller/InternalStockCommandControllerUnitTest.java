package com.sub9.productservice.product.presentation.command.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.stock.DeductStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.application.port.in.stock.OrderStockUseCase;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(InternalStockCommandController.class)
@DisplayName("InternalStockCommandController - 단위 테스트")
class InternalStockCommandControllerUnitTest extends AbstractControllerTest {
  @MockitoBean OrderStockUseCase orderStockUseCase;

  private final UUID orderId = UUID.randomUUID();
  private final UUID skuId = UUID.randomUUID();
  private final UUID secondSkuId = UUID.randomUUID();
  private final String endPoint = "/internal/v1/stocks";

  private String request() {
    return """
           {"orderId":"%s","items":[{"skuId":"%s","quantity":3},
             {"skuId":"%s","quantity":2}],"reason":"ORDER_CANCEL"}
           """
        .formatted(orderId, skuId, secondSkuId);
  }

  @Nested
  @DisplayName("재고 차감 테스트")
  class DeductTests {
    @Test
    @DisplayName("재고 차감에 성공하면 200을 반환한다.")
    void deduct_success() throws Exception {
      // given
      DeductStockCommand command =
          new DeductStockCommand(
              orderId,
              List.of(
                  new DeductStockCommand.Item(skuId, 3),
                  new DeductStockCommand.Item(secondSkuId, 2)));

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/deduct").contentType(MediaType.APPLICATION_JSON).content(request()))
          .andExpect(status().isOk());
      verify(orderStockUseCase).deduct(command);
    }

    @Test
    @DisplayName("재고 차감 실패 시 예외와 에러 코드를 반환한다.")
    void deduct_fails_when_service_rejects_request() throws Exception {
      // given
      ProductErrorCode errorCode = ProductErrorCode.INSUFFICIENT_STOCK;
      willThrow(new BusinessException(errorCode)).given(orderStockUseCase).deduct(any());

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/deduct").contentType(MediaType.APPLICATION_JSON).content(request()))
          .andExpect(status().is(errorCode.status().value()))
          .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }
  }

  @Nested
  @DisplayName("재고 복구 테스트")
  class RestoreTests {
    @Test
    @DisplayName("재고 복구에 성공하면 200을 반환한다.")
    void restore_success() throws Exception {
      // given
      RestoreStockCommand command =
          new RestoreStockCommand(
              orderId,
              List.of(
                  new RestoreStockCommand.Item(skuId, 3),
                  new RestoreStockCommand.Item(secondSkuId, 2)),
              StockHistoryReason.ORDER_CANCEL);

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/restore")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request()))
          .andExpect(status().isOk());
      verify(orderStockUseCase).restore(command);
    }

    @Test
    @DisplayName("재고 복구 실패 시 예외와 에러 코드를 반환한다.")
    void restore_fails_when_service_rejects_request() throws Exception {
      // given
      ProductErrorCode errorCode = ProductErrorCode.SKU_NOT_FOUND;
      willThrow(new BusinessException(errorCode)).given(orderStockUseCase).restore(any());

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/restore")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request()))
          .andExpect(status().is(errorCode.status().value()))
          .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }
  }

  @Nested
  @DisplayName("내부 통신 테스트")
  class ValidationTests {

    @ParameterizedTest
    @ValueSource(strings = {"deduct", "restore"})
    @DisplayName("주문 ID가 누락되면 400을 반환하고 서비스를 호출하지 않는다.")
    void request_fails_when_order_id_is_missing(String operation) throws Exception {
      // given
      String invalidRequest =
          request().replace(orderId.toString(), "").replace("\"orderId\":\"\"", "\"orderId\":null");

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/" + operation)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
      verifyNoInteractions(orderStockUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deduct", "restore"})
    @DisplayName("항목이 비어 있으면 400을 반환하고 서비스를 호출하지 않는다.")
    void request_fails_when_items_are_empty(String operation) throws Exception {
      // given
      String invalidRequest =
          "{\"orderId\":\"" + orderId + "\",\"items\":[],\"reason\":\"ORDER_CANCEL\"}";

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/" + operation)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
      verifyNoInteractions(orderStockUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deduct", "restore"})
    @DisplayName("SKU ID가 누락되면 400을 반환하고 서비스를 호출하지 않는다.")
    void request_fails_when_sku_id_is_missing(String operation) throws Exception {
      // given
      String invalidRequest = request().replace("\"" + skuId + "\"", "null");

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/" + operation)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
      verifyNoInteractions(orderStockUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"deduct", "restore"})
    @DisplayName("수량이 0이면 400을 반환하고 서비스를 호출하지 않는다.")
    void request_fails_when_quantity_is_zero(String operation) throws Exception {
      // given
      String invalidRequest = request().replace("\"quantity\":3", "\"quantity\":0");

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/" + operation)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
      verifyNoInteractions(orderStockUseCase);
    }

    @Test
    @DisplayName("복구 사유가 누락되면 400을 반환하고 서비스를 호출하지 않는다.")
    void restore_fails_when_reason_is_missing() throws Exception {
      // given
      String invalidRequest = request().replace("\"ORDER_CANCEL\"", "null");

      // when & then
      mockMvc
          .perform(
              post(endPoint + "/restore")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalidRequest))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
      verifyNoInteractions(orderStockUseCase);
    }
  }
}
