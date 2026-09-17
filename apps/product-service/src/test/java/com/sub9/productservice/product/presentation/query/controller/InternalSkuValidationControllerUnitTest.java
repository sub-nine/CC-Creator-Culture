package com.sub9.productservice.product.presentation.query.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.port.in.product.CartProductQueryUseCase;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.exception.SkuErrorCode;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(InternalSkuQueryController.class)
@DisplayName("InternalSkuQueryController - 장바구니 등록 검증 단위 테스트")
class InternalSkuValidationControllerUnitTest extends AbstractControllerTest {
  @MockitoBean CartProductQueryUseCase cartProductQueryUseCase;

  private final UUID skuId = UUID.randomUUID();
  private final String endPoint = "/internal/v1/skus/{skuId}/validation";

  @Test
  @DisplayName("상품 검증 성공 시 200과 productId을 반환한다.")
  void getValidatedProductIdForCart_success() throws Exception {
    UUID productId = UUID.randomUUID();
    org.mockito.BDDMockito.given(cartProductQueryUseCase.getValidatedProductIdForCart(skuId))
        .willReturn(productId);
    // when & then
    mockMvc
        .perform(get(endPoint, skuId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(productId.toString()));
    verify(cartProductQueryUseCase).getValidatedProductIdForCart(skuId);
  }

  @Test
  @DisplayName("품절 검증 실패 시 409와 SKU_SOLD_OUT 에러 코드를 반환한다.")
  void getValidatedProductIdForCart_fails_when_sku_is_sold_out() throws Exception {
    // given
    willThrow(new BusinessException(SkuErrorCode.SKU_SOLD_OUT))
        .given(cartProductQueryUseCase)
        .getValidatedProductIdForCart(skuId);

    // when & then
    mockMvc
        .perform(get(endPoint, skuId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value(SkuErrorCode.SKU_SOLD_OUT.code()));
    verify(cartProductQueryUseCase).getValidatedProductIdForCart(skuId);
  }
}
