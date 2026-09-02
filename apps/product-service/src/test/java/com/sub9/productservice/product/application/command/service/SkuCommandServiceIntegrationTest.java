package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.CreateSkuCommand;
import com.sub9.productservice.product.application.command.dto.DeleteSkuCommand;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.infrastructure.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("SkuCommandService - 통합 테스트")
class SkuCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired private SkuCommandService skuCommandService;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired SkuCommandJpaRepository skuRepository;
  @Autowired EntityManager entityManager;

  private static final UUID CREATOR_ID = UUID.randomUUID();
  private Product dummyProduct;
  private List<Sku> dummySku;

  @BeforeEach
  void setUp() {
    CreateProductCommand command =
        new CreateProductCommand(
            List.of("왁뿌볼", "말랑이"),
            CREATOR_ID,
            "말랑이",
            "말랑이 설명",
            List.of(
                new CreateSkuCommand("핑크", 10000L, true, 10),
                new CreateSkuCommand("블루", 12000L, false, 5)));

    Product product = Product.create(command.creatorId(), command.name(), command.content());

    dummyProduct = productRepository.save(product);

    dummySku =
        command.skus().stream()
            .map(
                skuCommand ->
                    Sku.create(
                        dummyProduct.getId(),
                        dummyProduct.getName(),
                        skuCommand.price(),
                        skuCommand.isDefault()))
            .collect(Collectors.toList());

    skuRepository.saveAll(dummySku);
  }

  @Nested
  @DisplayName("SKU 삭제 테스트")
  class DeleteSku {
    @Test
    @DisplayName("SKU 삭제 성공")
    void deleteSku_Success() {
      // given
      DeleteSkuCommand command =
          new DeleteSkuCommand(CREATOR_ID, dummyProduct.getId(), dummySku.get(1).getId());

      // when
      skuCommandService.deleteSku(command);

      entityManager.flush();
      entityManager.clear();

      Sku deletedSku = skuRepository.findById(dummySku.get(1).getId()).orElseThrow();

      // then
      assertThat(deletedSku.getDeletedAt()).isNotNull();
    }
  }
}
