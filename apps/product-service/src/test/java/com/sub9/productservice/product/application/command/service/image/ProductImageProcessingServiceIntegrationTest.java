package com.sub9.productservice.product.application.command.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
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
@DisplayName("ProductImageProcessingService - 통합 테스트")
class ProductImageProcessingServiceIntegrationTest extends AbstractIntegrationTest {
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

  @Autowired ProductImageProcessingService imageService;
  @MockitoBean ImageProcessorPort imageProcessorPort;

  @Test
  @DisplayName("리사이징에 성공하면 처리된 이미지 키와 완료 상태를 DB에 저장한다.")
  void resizeImage_success() {
    // given
    Image image = imageRepository.save(Image.create(product.getId(), "original/image", null, 0));

    flushAndClear();

    ImageData original = new ImageData("image/png", new byte[] {1});
    ImageData processed = new ImageData("image/jpeg", new byte[] {2});

    given(imageStoragePort.download(image.getOriginalKey())).willReturn(original);
    given(imageProcessorPort.resize(original)).willReturn(processed);

    // when
    imageService.resizeImage(image.getId(), product.getId(), image.getOriginalKey());
    flushAndClear();

    // then
    Image completed = imageRepository.findById(image.getId()).orElseThrow();
    assertThat(completed.getStatus()).isEqualTo(ImageProcessingStatus.COMPLETED);
    assertThat(completed.getProcessedKey()).isNotBlank().isNotEqualTo(completed.getOriginalKey());
    assertThat(completed.getProcessedAt()).isNotNull();
    assertThat(completed.getOriginalKey()).isEqualTo("original/image");
    verify(imageStoragePort).upload(completed.getProcessedKey(), processed);
  }
}
