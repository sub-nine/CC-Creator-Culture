package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.port.ImageData;
import com.sub9.productservice.product.application.port.ImageProcessorPort;
import com.sub9.productservice.product.application.port.ImageStoragePort;
import com.sub9.productservice.product.domain.model.Image;
import com.sub9.productservice.product.domain.model.ImageProcessingStatus;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ImageCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

@Transactional
@SpringBootTest
@DisplayName("ProductImageCommandService - 통합 테스트")
class ProductImageCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired ProductImageCommandService imageService;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired ImageCommandJpaRepository imageRepository;
  @Autowired EntityManager entityManager;
  @MockitoBean ImageStoragePort imageStoragePort;
  @MockitoBean ImageProcessorPort imageProcessorPort;
  @MockitoBean S3Client s3Client;

  private Image dummyImage;

  @BeforeEach
  void setUp() {
    UUID creatorId = UUID.randomUUID();
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));
    Product product = productRepository.save(Product.create(creatorId, "말랑이", "상품 설명"));
    dummyImage = imageRepository.save(Image.create(product.getId(), "original/image", null, 0));
    entityManager.flush();
    entityManager.clear();

    SecurityContextHolder.clearContext();
    ImageData original = new ImageData("image/png", new byte[] {1});
    ImageData processed = new ImageData("image/jpeg", new byte[] {2});
    given(imageStoragePort.download(dummyImage.getOriginalKey())).willReturn(original);
    given(imageProcessorPort.resize(original)).willReturn(processed);
    given(imageStoragePort.upload(anyString(), any()))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("리사이징 후 처리된 이미지 키와 완료 상태를 DB에 저장하고 재전송해도 기록을 중복 생성하지 않는다.")
  void resizeImage_success_when_event_is_retried() {
    // when
    imageService.resizeImage(
        dummyImage.getId(), dummyImage.getProductId(), dummyImage.getOriginalKey());
    entityManager.flush();
    entityManager.clear();

    // then
    Image completed = imageRepository.findById(dummyImage.getId()).orElseThrow();
    assertThat(completed.getStatus()).isEqualTo(ImageProcessingStatus.COMPLETED);
    assertThat(completed.getProcessedKey()).isNotBlank().isNotEqualTo(completed.getOriginalKey());
    assertThat(completed.getProcessedAt()).isNotNull();
    assertThat(completed.getOriginalKey()).isEqualTo(dummyImage.getOriginalKey());
    verify(imageStoragePort)
        .upload(
            eq(completed.getProcessedKey()),
            argThat(
                data ->
                    data.contentType().equals("image/jpeg")
                        && Arrays.equals(data.data(), new byte[] {2})));

    // when
    imageService.resizeImage(
        dummyImage.getId(), dummyImage.getProductId(), dummyImage.getOriginalKey());
    entityManager.flush();
    entityManager.clear();

    // then
    assertThat(imageRepository.findAllByProductIdAndDeletedAtIsNull(dummyImage.getProductId()))
        .hasSize(1);
    Image retried = imageRepository.findById(dummyImage.getId()).orElseThrow();
    assertThat(retried.getProcessedKey()).isEqualTo(completed.getProcessedKey());
    assertThat(retried.getProcessedAt()).isEqualTo(completed.getProcessedAt());
  }

  @Test
  @DisplayName("삭제 후 도착한 리사이징 이벤트는 이미지의 삭제 상태를 유지한다.")
  void resizeImage_success_when_image_was_deleted() {
    // given
    imageService.deleteAllImages(dummyImage.getProductId());
    entityManager.flush();
    entityManager.clear();

    // when
    imageService.resizeImage(
        dummyImage.getId(), dummyImage.getProductId(), dummyImage.getOriginalKey());
    entityManager.flush();
    entityManager.clear();

    // then
    Image deleted = imageRepository.findById(dummyImage.getId()).orElseThrow();
    assertThat(deleted.getDeletedAt()).isNotNull();
    assertThat(deleted.getStatus()).isEqualTo(ImageProcessingStatus.PENDING);
    assertThat(deleted.getProcessedKey()).isNull();
    assertThat(imageRepository.findAllByProductIdAndDeletedAtIsNull(dummyImage.getProductId()))
        .isEmpty();
  }
}
