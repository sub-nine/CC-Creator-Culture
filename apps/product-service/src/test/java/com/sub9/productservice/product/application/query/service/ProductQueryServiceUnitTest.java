package com.sub9.productservice.product.application.query.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.query.repository.ProductQueryRepository;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
