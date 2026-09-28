package com.sub9.productservice.product.infrastructure.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.port.out.product.ProductQueryRepository;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("ProductQueryRepository - 통합 테스트")
class ProductQueryRepositoryIntegrationTest extends AbstractIntegrationTest {
  @Autowired ProductQueryRepository productQueryRepository;
  @Autowired EntityManager entityManager;

  private final UUID creatorId = UUID.randomUUID();
  private Product product;
  private Sku sku;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));
    product = Product.create(creatorId, "말랑이", "상품 설명");
    entityManager.persist(product);
    sku = Sku.create(product.getId(), "핑크", 10000L, true);
    entityManager.persist(sku);
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({
    "ACTIVE, false, true, true",
    "INACTIVE, false, true, true",
    "SUSPENDED, false, true, false",
    "ACTIVE, true, true, false",
    "ACTIVE, false, false, false"
  })
  @DisplayName("삭제되지 않고 정지되지 않은 상품만 관심상품으로 등록 가능하다.")
  void existsById_returns_availability(
      ProductStatus status, boolean deleted, boolean exists, boolean expected) {
    // given
    entityManager
        .createQuery("UPDATE Product p SET p.status = :status WHERE p.id = :id")
        .setParameter("status", status)
        .setParameter("id", product.getId())
        .executeUpdate();
    if (deleted) {
      entityManager
          .createQuery("UPDATE Product p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
          .setParameter("id", product.getId())
          .executeUpdate();
    }
    entityManager.clear();

    // when
    boolean result =
        productQueryRepository.existsById(exists ? product.getId() : UUID.randomUUID());

    // then
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "true, true, false, false, true",
    "false, true, false, false, false",
    "true, false, false, false, false",
    "true, true, true, false, false",
    "true, true, false, true, false"
  })
  @DisplayName("상품과 SKU가 모두 삭제되지 않은 경우에만 재고 조정을 허용한다.")
  void existsSkuOwnedByCreatorId_returns_ownership(
      boolean owner, boolean exists, boolean productDeleted, boolean skuDeleted, boolean expected) {
    // given
    if (productDeleted) {
      entityManager
          .createQuery("UPDATE Product p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
          .setParameter("id", product.getId())
          .executeUpdate();
    }
    if (skuDeleted) {
      entityManager
          .createQuery("UPDATE Sku s SET s.deletedAt = CURRENT_TIMESTAMP WHERE s.id = :id")
          .setParameter("id", sku.getId())
          .executeUpdate();
    }
    entityManager.clear();

    // when
    boolean result =
        productQueryRepository.existsSkuOwnedByCreatorId(
            owner ? creatorId : UUID.randomUUID(), exists ? sku.getId() : UUID.randomUUID());

    // then
    assertThat(result).isEqualTo(expected);
  }
}
