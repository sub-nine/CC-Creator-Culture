package com.sub9.productservice.product.application.command.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.product.*;
import com.sub9.productservice.product.application.port.out.image.*;
import com.sub9.productservice.product.application.support.ImageStorageRollbackCleaner;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.*;
import com.sub9.productservice.product.domain.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageCommandService - 단위 테스트")
class ProductImageCommandServiceUnitTest {
  @Mock ProductRepository productRepository;
  @Mock ImageRepository imageRepository;
  @Mock ImageStoragePort imageStoragePort;
  @Mock ImageStorageRollbackCleaner imageStorageRollbackCleaner;
  @Mock ApplicationEventPublisher eventPublisher;
  @InjectMocks ProductImageCommandService imageService;

  private final UUID creatorId = UUID.randomUUID();
  private final Product product = Product.create(creatorId, "말랑이", "상품 설명");

  @Test
  @DisplayName("후속 이미지가 유효하지 않으면 앞쪽 이미지도 저장하지 않는다.")
  void uploadImages_fails_before_any_write_when_later_image_is_invalid() throws Exception {
    // given
    var images =
        List.of(
            new UploadImageCommand(
                "image/png", com.sub9.productservice.support.ImageTestFixture.imageBytes("png")),
            new UploadImageCommand("image/png", new byte[0]));

    // when & then
    assertThatThrownBy(() -> imageService.uploadImages(product.getId(), images))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(imageRepository, imageStoragePort, eventPublisher);
  }

  @Test
  @DisplayName("상품이 존재하지 않으면 이미지 순서 변경에 실패한다.")
  void updateSortOrder_fails_when_product_not_found() {
    // given
    given(productRepository.findByIdForUpdate(product.getId())).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(
            () ->
                imageService.updateSortOrder(
                    new UpdateImageSortOrderCommand(
                        product.getId(), creatorId, List.of(UUID.randomUUID()))))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.message());
  }

  @Test
  @DisplayName("상품 소유자가 아니면 이미지 순서 변경에 실패한다.")
  void updateSortOrder_fails_when_creator_is_not_owner() {
    // given
    given(productRepository.findByIdForUpdate(product.getId())).willReturn(Optional.of(product));

    // when & then
    assertThatThrownBy(
            () ->
                imageService.updateSortOrder(
                    new UpdateImageSortOrderCommand(
                        product.getId(), UUID.randomUUID(), List.of(UUID.randomUUID()))))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ProductErrorCode.PRODUCT_ACCESS_DENIED.message());
    verifyNoInteractions(imageRepository);
  }

  @Test
  @DisplayName("이미지 ID가 중복되거나 누락되거나 다른 상품의 이미지이면 순서 변경에 실패한다.")
  void updateSortOrder_fails_when_image_ids_are_invalid() {
    // given
    Image first = Image.create(product.getId(), "original/first", null, 0);
    Image second = Image.create(product.getId(), "original/second", null, 1);

    given(productRepository.findByIdForUpdate(product.getId())).willReturn(Optional.of(product));
    given(imageRepository.findAllByProductIdAndDeletedAtIsNull(product.getId()))
        .willReturn(List.of(first, second));

    var invalidRequests =
        List.of(
            List.of(first.getId(), second.getId(), first.getId()),
            List.of(first.getId()),
            List.of(first.getId(), UUID.randomUUID()));

    // when & then
    for (var imageIds : invalidRequests) {
      assertThatThrownBy(
              () ->
                  imageService.updateSortOrder(
                      new UpdateImageSortOrderCommand(product.getId(), creatorId, imageIds)))
          .isInstanceOf(BusinessException.class)
          .hasMessage(ProductErrorCode.INVALID_PRODUCT_IMAGE_INFO.message());
    }
    assertThat(first.getSortOrder()).isZero();
    assertThat(second.getSortOrder()).isEqualTo(1);
  }

  @Test
  @DisplayName("상품 소유자가 아니면 이미지 삭제에 실패한다.")
  void delete_fails_when_creator_is_not_owner() {
    // given
    given(productRepository.findByIdForUpdate(product.getId())).willReturn(Optional.of(product));

    // when & then
    assertThatThrownBy(
            () ->
                imageService.delete(
                    new DeleteProductImageCommand(
                        UUID.randomUUID(), product.getId(), UUID.randomUUID())))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ProductErrorCode.PRODUCT_ACCESS_DENIED.message());
    verifyNoInteractions(imageRepository);
  }

  @Test
  @DisplayName("삭제할 이미지가 없으면 PRODUCT_IMAGE_NOT_FOUND 예외가 발생한다.")
  void delete_fails_when_image_not_found() {
    // given
    UUID imageId = UUID.randomUUID();
    given(productRepository.findByIdForUpdate(product.getId())).willReturn(Optional.of(product));
    given(imageRepository.softDelete(imageId, product.getId(), creatorId)).willReturn(false);

    // when & then
    assertThatThrownBy(
            () ->
                imageService.delete(
                    new DeleteProductImageCommand(creatorId, product.getId(), imageId)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ProductErrorCode.PRODUCT_IMAGE_NOT_FOUND.message());
  }
}
