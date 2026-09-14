package com.sub9.productservice.product.application.command.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.command.dto.product.*;
import com.sub9.productservice.product.application.port.out.image.*;
import com.sub9.productservice.product.domain.model.*;
import com.sub9.productservice.product.infrastructure.persistence.command.product.*;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("ProductImageCommandService - 통합 테스트")
class ProductImageCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired ImageCommandJpaRepository imageRepository;
  @Autowired EntityManager entityManager;
  @MockitoBean ImageStoragePort imageStoragePort;
  private final UUID creatorId = UUID.randomUUID();
  private Product product;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));
    product = productRepository.save(Product.create(creatorId, "말랑이", "상품 설명"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  @Autowired ProductImageCommandService imageService;

  @Test
  @DisplayName("이미지를 첨부하지 않으면 이미지가 저장되지 않는다.")
  void uploadImages_success_without_images() {
    // when
    imageService.uploadImages(product.getId(), List.of());
    imageService.uploadImages(product.getId(), null);
    flushAndClear();

    // then
    assertThat(imageRepository.findAllByProductIdAndDeletedAtIsNull(product.getId())).isEmpty();
    verifyNoInteractions(imageStoragePort);
  }

  @Test
  @DisplayName("이미지 순서 변경에 성공하면 요청한 순서대로 DB에 반영한다.")
  void updateSortOrder_success() {
    // given
    Image first = imageRepository.save(Image.create(product.getId(), "original/first", null, 0));
    Image second = imageRepository.save(Image.create(product.getId(), "original/second", null, 1));
    flushAndClear();

    // when
    imageService.updateSortOrder(
        new UpdateImageSortOrderCommand(
            product.getId(), creatorId, List.of(second.getId(), first.getId())));
    flushAndClear();

    // then
    assertThat(imageRepository.findById(second.getId()).orElseThrow().getSortOrder()).isZero();
    assertThat(imageRepository.findById(first.getId()).orElseThrow().getSortOrder()).isEqualTo(1);
  }

  @Test
  @DisplayName("이미지 삭제 후 순서를 변경해도 삭제된 이미지와 원본 키는 유지한다.")
  void delete_success_and_reorder_remaining_images() {
    // given
    Image first = imageRepository.save(Image.create(product.getId(), "original/first", null, 0));
    Image second =
        imageRepository.save(
            Image.create(product.getId(), "original/second", "processed/second", 1));
    flushAndClear();

    // when
    imageService.delete(new DeleteProductImageCommand(creatorId, product.getId(), first.getId()));
    flushAndClear();

    imageService.updateSortOrder(
        new UpdateImageSortOrderCommand(product.getId(), creatorId, List.of(second.getId())));
    flushAndClear();

    // then
    Image deleted = imageRepository.findById(first.getId()).orElseThrow();
    assertThat(deleted.getDeletedAt()).isNotNull();
    assertThat(deleted.getOriginalKey()).isEqualTo("original/first");
    assertThat(imageRepository.findAllByProductIdAndDeletedAtIsNull(product.getId()))
        .extracting(Image::getId)
        .containsExactly(second.getId());
    assertThat(imageRepository.findById(second.getId()).orElseThrow().getSortOrder()).isZero();
    verifyNoInteractions(imageStoragePort);
  }
}
