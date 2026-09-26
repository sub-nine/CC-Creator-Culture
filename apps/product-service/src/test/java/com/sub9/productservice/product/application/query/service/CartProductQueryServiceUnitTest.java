package com.sub9.productservice.product.application.query.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.port.out.product.ProductQueryRepository;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.exception.SkuErrorCode;
import com.sub9.productservice.product.domain.model.ProductStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartProductQueryService - 단위 테스트")
class CartProductQueryServiceUnitTest {
  @Mock ProductQueryRepository productQueryRepository;
  @InjectMocks private CartProductQueryService cartProductQueryService;

  private final UUID productId = UUID.randomUUID();

  @Nested
  @DisplayName("장바구니 등록 전 상품 검증 테스트")
  class GetValidatedProductIdForCartTests {
    private final UUID skuId = UUID.randomUUID();

    @Test
    @DisplayName("조회 결과가 없으면 PRODUCT_NOT_FOUND 예외가 발생한다.")
    void getValidatedProductIdForCart_fails_when_product_not_found() {
      // given
      given(productQueryRepository.getCartItemProducts(List.of(skuId))).willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> cartProductQueryService.getValidatedProductIdForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());
    }

    @ParameterizedTest
    @EnumSource(
        value = ProductStatus.class,
        names = {"INACTIVE", "SUSPENDED"})
    @DisplayName("판매 중이 아니면 PRODUCT_NOT_FOR_SALE 예외가 발생한다.")
    void getValidatedProductIdForCart_fails_when_product_is_not_active(ProductStatus status) {
      // given
      SkuInfo info =
          new SkuInfo(skuId, productId, UUID.randomUUID(), "말랑이", "핑크", status, 10000L, 1);

      given(productQueryRepository.getCartItemProducts(List.of(skuId))).willReturn(List.of(info));

      // when & then
      assertThatThrownBy(() -> cartProductQueryService.getValidatedProductIdForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOR_SALE.message());
    }

    @Test
    @DisplayName("판매 중이어도 재고가 없으면 SKU_SOLD_OUT 예외가 발생한다.")
    void getValidatedProductIdForCart_fails_when_stock_is_zero() {
      // given
      SkuInfo info =
          new SkuInfo(
              skuId, productId, UUID.randomUUID(), "말랑이", "핑크", ProductStatus.ACTIVE, 10000L, 0);

      given(productQueryRepository.getCartItemProducts(List.of(skuId))).willReturn(List.of(info));

      // when & then
      assertThatThrownBy(() -> cartProductQueryService.getValidatedProductIdForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(SkuErrorCode.SKU_SOLD_OUT.message());
    }
  }
}
