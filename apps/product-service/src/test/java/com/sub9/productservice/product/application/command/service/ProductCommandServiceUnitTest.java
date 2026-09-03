package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.UpdateProductCommand;
import com.sub9.productservice.product.application.command.dto.UpdateProductStatusCommand;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import com.sub9.productservice.product.domain.repository.SkuCommandRepository;
import com.sub9.productservice.product.domain.repository.StockCommandRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCommandService - 단위 테스트")
class ProductCommandServiceUnitTest {
  @Mock private ProductCommandRepository productCommandRepository;
  @Mock private SkuCommandRepository skuCommandRepository;
  @Mock private StockCommandRepository stockCommandRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private ProductCommandService productCommandService;

  private final UUID creatorId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();

  @Nested
  @DisplayName("상품 정보 수정 테스트")
  class UpdateProductTests {
    @Test
    @DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외가 발생해야 한다.")
    void updateProduct_fails_when_product_not_found() {
      // given
      UpdateProductCommand command =
          new UpdateProductCommand(creatorId, productId, "수정된 상품명", "수정된 상품 설명");
      given(productCommandRepository.findByIdAndDeletedAtIsNull(productId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> productCommandService.updateProduct(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());
    }

    @Test
    @DisplayName("상품 소유자가 아니면 PRODUCT_ACCESS_DENIED 예외가 발생해야 한다.")
    void updateProduct_fails_when_product_access_denied() {
      // given
      Product product = mock(Product.class);
      UpdateProductCommand command =
          new UpdateProductCommand(creatorId, productId, "수정된 상품명", "수정된 상품 설명");

      given(productCommandRepository.findByIdAndDeletedAtIsNull(productId))
          .willReturn(Optional.of(product));
      willThrow(new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED))
          .given(product)
          .validateOwner(creatorId);

      // when & then
      assertThatThrownBy(() -> productCommandService.updateProduct(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_ACCESS_DENIED.message());

      verify(product, never()).update(command.name(), command.content());
    }
  }

  @Nested
  @DisplayName("상품 상태 수정 테스트")
  class UpdateStatusProductTests {
    @Test
    @DisplayName("관리자는 소유권 검증 없이 상품 상태를 수정할 수 있어야 한다.")
    void updateStatusProduct_success_for_admin() {
      // given
      Product product = mock(Product.class);
      UpdateProductStatusCommand command =
          new UpdateProductStatusCommand(
              UUID.randomUUID(), productId, "MASTER", ProductStatus.ACTIVE.name());

      given(productCommandRepository.findByIdAndDeletedAtIsNull(productId))
          .willReturn(Optional.of(product));

      // when
      productCommandService.updateStatusProduct(command);

      // then
      verify(product, never()).validateOwner(command.userId());
      verify(product).updateStatus(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외가 발생해야 한다.")
    void updateStatusProduct_fails_when_product_not_found() {
      // given
      UpdateProductStatusCommand command =
          new UpdateProductStatusCommand(
              creatorId, productId, "CREATOR", ProductStatus.INACTIVE.name());
      given(productCommandRepository.findByIdAndDeletedAtIsNull(productId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> productCommandService.updateStatusProduct(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());
    }

    @Test
    @DisplayName("창작자가 상품 소유자가 아니면 PRODUCT_ACCESS_DENIED 예외가 발생해야 한다.")
    void updateStatusProduct_fails_when_creator_is_not_owner() {
      // given
      Product product = mock(Product.class);
      UpdateProductStatusCommand command =
          new UpdateProductStatusCommand(
              creatorId, productId, "CREATOR", ProductStatus.INACTIVE.name());

      given(productCommandRepository.findByIdAndDeletedAtIsNull(productId))
          .willReturn(Optional.of(product));
      willThrow(new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED))
          .given(product)
          .validateOwner(creatorId);

      // when & then
      assertThatThrownBy(() -> productCommandService.updateStatusProduct(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_ACCESS_DENIED.message());

      verify(product, never()).updateStatus(ProductStatus.INACTIVE);
    }
  }

  @Nested
  @DisplayName("상품 삭제 테스트")
  class DeleteProductTests {
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
