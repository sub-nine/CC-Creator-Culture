package com.sub9.productservice.product.application.query.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.product.application.query.repository.ProductQueryRepository;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.ProductStatus;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductQueryService - 단위 테스트")
class ProductQueryServiceUnitTest {
  @Mock ProductQueryRepository productQueryRepository;
  @InjectMocks private ProductQueryService productQueryService;

  private final UUID productId = UUID.randomUUID();

  @Test
  @DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외가 발생해야 한다.")
  void getProductDetail_fails_when_product_not_found() {
    // given
    given(productQueryRepository.findProductDetailById(productId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> productQueryService.getProductDetail(productId, null))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());
  }

  @Test
  @DisplayName("상품 검색 중 Repository 예외가 발생하면 예외를 전파한다.")
  void searchProducts_fails_when_repository_throws_exception() {
    // given
    String keyword = "왁뿌볼";
    PageRequest pageable = PageRequest.of(0, 10);
    RuntimeException exception = new RuntimeException("상품 조회 실패");

    given(productQueryRepository.searchProducts(keyword, pageable)).willThrow(exception);

    // when & then
    assertThatThrownBy(() -> productQueryService.searchProducts(keyword, pageable))
        .isSameAs(exception);
  }

  @Nested
  @DisplayName("장바구니 등록 전 상품 검증 테스트")
  class ValidateSkuForCartTests {
    private final UUID skuId = UUID.randomUUID();

    @Test
    @DisplayName("조회 결과가 없으면 PRODUCT_NOT_FOUND 예외가 발생한다.")
    void validateSkuForCart_fails_when_product_not_found() {
      // given
      given(productQueryRepository.getCartItemProducts(List.of(skuId))).willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> productQueryService.validateSkuForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());
    }

    @ParameterizedTest
    @EnumSource(
        value = ProductStatus.class,
        names = {"INACTIVE", "SUSPENDED"})
    @DisplayName("판매 중이 아니면 PRODUCT_NOT_FOR_SALE 예외가 발생한다.")
    void validateSkuForCart_fails_when_product_is_not_active(ProductStatus status) {
      // given
      SkuInfo info =
          new SkuInfo(skuId, productId, UUID.randomUUID(), "말랑이", "핑크", status, 10000L, 1);
      given(productQueryRepository.getCartItemProducts(List.of(skuId))).willReturn(List.of(info));

      // when & then
      assertThatThrownBy(() -> productQueryService.validateSkuForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOR_SALE.message());
    }

    @Test
    @DisplayName("판매 중이어도 재고가 0이면 SKU_SOLD_OUT 예외가 발생한다.")
    void validateSkuForCart_fails_when_stock_is_zero() {
      // given
      SkuInfo info =
          new SkuInfo(
              skuId, productId, UUID.randomUUID(), "말랑이", "핑크", ProductStatus.ACTIVE, 10000L, 0);
      given(productQueryRepository.getCartItemProducts(List.of(skuId))).willReturn(List.of(info));

      // when & then
      assertThatThrownBy(() -> productQueryService.validateSkuForCart(skuId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.SKU_SOLD_OUT.message());
    }
  }
}
