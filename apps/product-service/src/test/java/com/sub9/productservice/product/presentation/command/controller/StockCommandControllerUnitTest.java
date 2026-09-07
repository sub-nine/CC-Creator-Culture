package com.sub9.productservice.product.presentation.command.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.command.dto.stock.AdjustStockCommand;
import com.sub9.productservice.product.application.command.service.StockCommandService;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(StockCommandController.class)
@DisplayName("StockCommandController - 단위 테스트")
class StockCommandControllerUnitTest extends AbstractControllerTest {
  @MockitoBean StockCommandService stockCommandService;
  private final UUID creatorId = UUID.randomUUID();
  private final UUID skuId = UUID.randomUUID();
  private final String endPoint = "/api/v1/stock/{skuId}/adjustments";

  @Nested
  @DisplayName("재고 조정 테스트")
  class AdjustTests {
    @Test
    @DisplayName("창작자가 재고를 조정하면 200과 성공 메시지를 반환한다.")
    void adjustStock_success() throws Exception {
      // given
      int quantity = -5;
      AdjustStockCommand command = new AdjustStockCommand(creatorId, skuId, quantity);

      // when & then
      mockMvc
          .perform(
              post(endPoint, skuId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"quantity\":" + quantity + "}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("상품 수량이 변경되었습니다."))
          .andExpect(jsonPath("$.data").doesNotExist());
      verify(stockCommandService).adjust(command);
    }

    @Test
    @DisplayName("재고 조정 실패 시 비즈니스 예외의 상태와 에러 코드를 반환한다.")
    void adjustStock_fails_when_service_rejects_request() throws Exception {
      // given
      ProductErrorCode errorCode = ProductErrorCode.INSUFFICIENT_STOCK;
      int quantity = -11;
      willThrow(new BusinessException(errorCode)).given(stockCommandService).adjust(any());

      // when & then
      mockMvc
          .perform(
              post(endPoint, skuId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"quantity\":" + quantity + "}"))
          .andExpect(status().is(errorCode.status().value()))
          .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }

    @Test
    @DisplayName("창작자 권한이 없으면 403을 반환하고 서비스를 호출하지 않는다.")
    void adjustStock_fails_when_role_is_not_creator() throws Exception {
      // when & then
      mockMvc
          .perform(
              post(endPoint, skuId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "MANAGER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"quantity\":5}"))
          .andExpect(status().isForbidden());
      verifyNoInteractions(stockCommandService);
    }
  }
}
