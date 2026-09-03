package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import com.sub9.productservice.product.domain.repository.SkuCommandRepository;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCommandService - 단위 테스트")
class ProductCommandServiceUnitTest {
  @Nested
  @DisplayName("상품 삭제 테스트")
  class deleteProductTest {
    @Mock private ProductCommandRepository productCommandRepository;
    @Mock private SkuCommandRepository skuCommandRepository;
    @InjectMocks private ProductCommandService productCommandService;

    @Test
    @DisplayName("상품이 존재하지 않는 경우 PRODUCT_NOT_FOUND 예외가 발생해야한다.")
    void deleteProduct_fails_when_product_not_found() {
      // given
      UUID creatorId = UUID.randomUUID();
      UUID productId = UUID.randomUUID();

      given(productCommandRepository.findByIdForUpdate(productId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> productCommandService.deleteProduct(creatorId, productId))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());
    }
  }
}
