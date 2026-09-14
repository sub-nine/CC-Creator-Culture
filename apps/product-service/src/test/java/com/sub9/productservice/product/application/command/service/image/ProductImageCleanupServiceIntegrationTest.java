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
@DisplayName("ProductImageCleanupService - 통합 테스트")
class ProductImageCleanupServiceIntegrationTest extends AbstractIntegrationTest {
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

  @Autowired ProductImageCleanupService imageService;

  @Test
  @DisplayName("복구 기한이 지난 상품과 개별 삭제 이미지만 파일과 DB에서 제거한다.")
  void deleteExpiredImages_success() {
    // given
    Instant now = Instant.parse("2026-09-11T00:00:00Z");
    Instant cutoff = now.minusSeconds(7 * 86400);

    Image expired =
        imageRepository.save(Image.create(product.getId(), "original/expired", null, 0));
    Image recent = imageRepository.save(Image.create(product.getId(), "original/recent", null, 1));
    Image active = imageRepository.save(Image.create(product.getId(), "original/active", null, 2));

    Product deletedProduct = productRepository.save(Product.create(creatorId, "삭제 상품", "설명"));

    Image parentDeleted =
        imageRepository.save(
            Image.create(deletedProduct.getId(), "original/parent", "processed/parent", 0));

    entityManager.flush();
    entityManager
        .createQuery("UPDATE Image i SET i.deletedAt = :at WHERE i.id = :id")
        .setParameter("at", cutoff)
        .setParameter("id", expired.getId())
        .executeUpdate();
    entityManager
        .createQuery("UPDATE Image i SET i.deletedAt = :at WHERE i.id = :id")
        .setParameter("at", cutoff.plusSeconds(1))
        .setParameter("id", recent.getId())
        .executeUpdate();
    entityManager
        .createQuery("UPDATE Product p SET p.deletedAt = :at WHERE p.id = :id")
        .setParameter("at", cutoff)
        .setParameter("id", deletedProduct.getId())
        .executeUpdate();
    entityManager.clear();

    // when
    imageService.deleteExpiredImages(now);
    flushAndClear();

    // then
    assertThat(imageRepository.findById(expired.getId())).isEmpty();
    assertThat(imageRepository.findById(parentDeleted.getId())).isEmpty();
    assertThat(imageRepository.findById(recent.getId())).isPresent();
    assertThat(imageRepository.findById(active.getId())).isPresent();
    verify(imageStoragePort).delete("original/expired");
    verify(imageStoragePort).delete("original/parent");
    verify(imageStoragePort).delete("processed/parent");
    verifyNoMoreInteractions(imageStoragePort);
  }
}
