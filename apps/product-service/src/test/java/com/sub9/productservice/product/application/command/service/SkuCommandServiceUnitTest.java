package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.dto.UpdateSkuCommand;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import com.sub9.productservice.product.domain.repository.SkuCommandRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("SkuCommandService - 단위 테스트")
@ExtendWith(MockitoExtension.class)
class SkuCommandServiceUnitTest {
  @Mock private ProductCommandRepository productCommandRepository;
  @Mock private SkuCommandRepository skuCommandRepository;
  @InjectMocks private SkuCommandService skuCommandService;

  private final UUID creatorId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID skuId = UUID.randomUUID();

  private UpdateSkuCommand defaultUpdateSkuCommand;
  private UpdateSkuCommand updateSkuCommand;
  private DeleteSkuCommand deleteSkuCommand;

  @BeforeEach
  void setUp() {
    defaultUpdateSkuCommand =
        new UpdateSkuCommand(creatorId, productId, skuId, "옵션이름", 2000L, true);
    updateSkuCommand = new UpdateSkuCommand(creatorId, productId, skuId, "옵션이름", 2000L, false);
    deleteSkuCommand = new DeleteSkuCommand(creatorId, productId, skuId);
  }

  @Nested
  @DisplayName("SKU 수정 테스트")
  class updateSkuTest {
    @Test
    @DisplayName("일반 SKU를 대표 SKU로 변경하면 기존 대표 SKU가 해제되어야 한다.")
    void updateSku_success_when_changing_default_sku() {
      // given
      Product product = mock(Product.class);
      Sku sku = mock(Sku.class);
      Sku currentDefaultSku = mock(Sku.class);

      given(productCommandRepository.findByIdForUpdate(defaultUpdateSkuCommand.productId()))
          .willReturn(Optional.of(product));
      given(
              skuCommandRepository.findByIdAndProductIdAndDeletedAtIsNull(
                  defaultUpdateSkuCommand.skuId(), defaultUpdateSkuCommand.productId()))
          .willReturn(Optional.of(sku));
      given(sku.isDefault()).willReturn(false);
      given(
              skuCommandRepository.findByProductIdAndIsDefaultTrue(
                  defaultUpdateSkuCommand.productId()))
          .willReturn(Optional.of(currentDefaultSku));

      // when
      skuCommandService.updateSku(defaultUpdateSkuCommand);

      // then
      verify(product).validateOwner(defaultUpdateSkuCommand.creatorId());
      verify(currentDefaultSku).unsetDefault();
      verify(sku)
          .update(
              defaultUpdateSkuCommand.name(),
              defaultUpdateSkuCommand.price(),
              defaultUpdateSkuCommand.isDefault());
    }

    @Test
    @DisplayName("기존 대표 SKU가 없으면 DEFAULT_SKU_NOT_FOUND 예외가 발생해야 한다.")
    void updateSku_fails_when_default_sku_not_found() {
      // given
      Product product = mock(Product.class);
      Sku sku = mock(Sku.class);

      given(productCommandRepository.findByIdForUpdate(defaultUpdateSkuCommand.productId()))
          .willReturn(Optional.of(product));
      given(
              skuCommandRepository.findByIdAndProductIdAndDeletedAtIsNull(
                  defaultUpdateSkuCommand.skuId(), defaultUpdateSkuCommand.productId()))
          .willReturn(Optional.of(sku));
      given(sku.isDefault()).willReturn(false);
      given(
              skuCommandRepository.findByProductIdAndIsDefaultTrue(
                  defaultUpdateSkuCommand.productId()))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> skuCommandService.updateSku(defaultUpdateSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.DEFAULT_SKU_NOT_FOUND.message());

      verify(sku, never()).update(anyString(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외가 발생해야 한다.")
    void updateSku_fails_when_product_not_found() {
      // given
      given(productCommandRepository.findByIdForUpdate(updateSkuCommand.productId()))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> skuCommandService.updateSku(updateSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());

      verifyNoInteractions(skuCommandRepository);
    }

    @Test
    @DisplayName("상품 소유자가 아니면 PRODUCT_ACCESS_DENIED 예외가 발생해야 한다.")
    void updateSku_fails_when_product_access_denied() {
      // given
      Product product = mock(Product.class);

      given(productCommandRepository.findByIdForUpdate(updateSkuCommand.productId()))
          .willReturn(Optional.of(product));
      willThrow(new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED))
          .given(product)
          .validateOwner(updateSkuCommand.creatorId());

      // when & then
      assertThatThrownBy(() -> skuCommandService.updateSku(updateSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_ACCESS_DENIED.message());

      verifyNoInteractions(skuCommandRepository);
    }

    @Test
    @DisplayName("SKU가 존재하지 않으면 SKU_NOT_FOUND 예외가 발생해야 한다.")
    void updateSku_fails_when_sku_not_found() {
      // given
      Product product = mock(Product.class);

      given(productCommandRepository.findByIdForUpdate(updateSkuCommand.productId()))
          .willReturn(Optional.of(product));
      given(
              skuCommandRepository.findByIdAndProductIdAndDeletedAtIsNull(
                  updateSkuCommand.skuId(), updateSkuCommand.productId()))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> skuCommandService.updateSku(updateSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.SKU_NOT_FOUND.message());

      verify(product).validateOwner(updateSkuCommand.creatorId());
    }

    @Test
    @DisplayName("대표 SKU를 false로 변경하면 DEFAULT_SKU_CANNOT_UNSET 예외가 발생해야 한다.")
    void updateSku_fails_when_default_sku_cannot_unset() {
      // given
      Product product = mock(Product.class);
      Sku sku = mock(Sku.class);

      given(productCommandRepository.findByIdForUpdate(updateSkuCommand.productId()))
          .willReturn(Optional.of(product));

      given(
              skuCommandRepository.findByIdAndProductIdAndDeletedAtIsNull(
                  updateSkuCommand.skuId(), updateSkuCommand.productId()))
          .willReturn(Optional.of(sku));

      given(sku.isDefault()).willReturn(true);

      // when & then
      assertThatThrownBy(() -> skuCommandService.updateSku(updateSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.DEFAULT_SKU_CANNOT_UNSET.message());

      verify(product).validateOwner(updateSkuCommand.creatorId());
      verify(sku).isDefault();
      verify(sku, never()).update(anyString(), anyLong(), anyBoolean());
    }
  }

  @Nested
  @DisplayName("SKU 삭제 테스트")
  class DeleteSkuTests {
    @Test
    @DisplayName("상품이 존재하지 않는 경우 PRODUCT_NOT_FOUND 예외가 발생해야한다.")
    void deleteSku_fails_when_product_not_found() {
      // given
      given(productCommandRepository.findByIdForUpdate(deleteSkuCommand.productId()))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> skuCommandService.deleteSku(deleteSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());

      verify(productCommandRepository).findByIdForUpdate(deleteSkuCommand.productId());
      verifyNoInteractions(skuCommandRepository);
    }

    @Test
    @DisplayName("상품 소유자가 다른 경우 PRODUCT_ACCESS_DENIED 예외가 발생해야한다.")
    void deleteSku_fails_when_product_access_denied() {
      // given
      Product product = mock(Product.class);

      given(productCommandRepository.findByIdForUpdate(deleteSkuCommand.productId()))
          .willReturn(Optional.of(product));

      willThrow(new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED))
          .given(product)
          .validateOwner(deleteSkuCommand.creatorId());

      // when & then
      assertThatThrownBy(() -> skuCommandService.deleteSku(deleteSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.PRODUCT_ACCESS_DENIED.message());

      verify(product).validateOwner(deleteSkuCommand.creatorId());
      verifyNoInteractions(skuCommandRepository);
    }

    @Test
    @DisplayName("SKU가 존재하지 않는 경우 SKU_NOT_FOUND 예외가 발생해야 한다.")
    void deleteSku_fails_when_sku_not_found() {
      // given
      Product product = mock(Product.class);

      given(productCommandRepository.findByIdForUpdate(deleteSkuCommand.productId()))
          .willReturn(Optional.of(product));

      given(
              skuCommandRepository.findByIdAndProductIdAndDeletedAtIsNull(
                  deleteSkuCommand.skuId(), deleteSkuCommand.productId()))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> skuCommandService.deleteSku(deleteSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.SKU_NOT_FOUND.message());

      verify(skuCommandRepository)
          .findByIdAndProductIdAndDeletedAtIsNull(
              deleteSkuCommand.skuId(), deleteSkuCommand.productId());
    }

    @Test
    @DisplayName("대표 SKU를 삭제하려는 경우 DEFAULT_SKU_CANNOT_DELETED 예외가 발생해야 한다.")
    void deleteSku_fails_when_default_sku() {
      // given
      Product product = mock(Product.class);
      Sku sku = mock(Sku.class);

      given(productCommandRepository.findByIdForUpdate(deleteSkuCommand.productId()))
          .willReturn(Optional.of(product));

      given(
              skuCommandRepository.findByIdAndProductIdAndDeletedAtIsNull(
                  deleteSkuCommand.skuId(), deleteSkuCommand.productId()))
          .willReturn(Optional.of(sku));

      given(sku.isDefault()).willReturn(true);

      // when & then
      assertThatThrownBy(() -> skuCommandService.deleteSku(deleteSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.DEFAULT_SKU_CANNOT_DELETED.message());

      verify(sku).isDefault();
      verify(sku, never()).delete(any());
    }

    @Test
    @DisplayName("SKU가 1개 이하인 경우 SKU_REQUIRED 예외가 발생해야 한다.")
    void deleteSku_fails_when_sku_exists() {
      // given
      Product product = mock(Product.class);
      Sku sku = mock(Sku.class);

      given(productCommandRepository.findByIdForUpdate(deleteSkuCommand.productId()))
          .willReturn(Optional.of(product));

      given(
              skuCommandRepository.findByIdAndProductIdAndDeletedAtIsNull(
                  deleteSkuCommand.skuId(), deleteSkuCommand.productId()))
          .willReturn(Optional.of(sku));

      given(skuCommandRepository.countByProductIdAndDeletedAtIsNull(deleteSkuCommand.productId()))
          .willReturn(1L);

      given(sku.isDefault()).willReturn(false);

      // when & then
      assertThatThrownBy(() -> skuCommandService.deleteSku(deleteSkuCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.SKU_REQUIRED.message());

      verify(sku, never()).delete(any());
    }
  }
}
