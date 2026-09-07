package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.CreateSkuCommand;
import com.sub9.productservice.product.application.command.dto.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.dto.UpdateSkuCommand;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.sku.SkuCommandJpaRepository;
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

  private final UUID creatorId = UUID.randomUUID();
  private Product dummyProduct;
  private List<Sku> dummySku;

  @BeforeEach
  void setUp() {
    CreateProductCommand command =
        new CreateProductCommand(
            List.of("왁뿌볼", "말랑이"),
            creatorId,
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
                        skuCommand.name(),
                        skuCommand.price(),
                        skuCommand.isDefault()))
            .collect(Collectors.toList());

    skuRepository.saveAll(dummySku);
  }

  @Nested
  @DisplayName("SKU 수정 테스트")
  class UpdateSku {
    @Test
    @DisplayName("일반 SKU 정보 수정에 성공한다.")
    void updateSku_success() {
      // given
      Sku targetSku = dummySku.get(1);
      UpdateSkuCommand command =
          new UpdateSkuCommand(
              creatorId, dummyProduct.getId(), targetSku.getId(), "수정된 옵션", 15000L, false);

      // when
      skuCommandService.updateSku(command);

      entityManager.flush();
      entityManager.clear();

      // then
      Sku updatedSku = skuRepository.findById(targetSku.getId()).orElseThrow();

      assertThat(updatedSku.getName()).isEqualTo(command.name());
      assertThat(updatedSku.getPrice()).isEqualTo(command.price());
      assertThat(updatedSku.isDefault()).isFalse();
    }

    @Test
    @DisplayName("일반 SKU를 대표 SKU로 수정하면 기존 대표 SKU가 해제된다.")
    void updateSku_success_when_changing_default_sku() {
      // given
      Sku currentDefaultSku = dummySku.get(0);
      Sku targetSku = dummySku.get(1);
      UpdateSkuCommand command =
          new UpdateSkuCommand(
              creatorId,
              dummyProduct.getId(),
              targetSku.getId(),
              targetSku.getName(),
              targetSku.getPrice(),
              true);

      // when
      skuCommandService.updateSku(command);

      entityManager.flush();
      entityManager.clear();

      // then
      Sku previousDefaultSku = skuRepository.findById(currentDefaultSku.getId()).orElseThrow();
      Sku updatedDefaultSku = skuRepository.findById(targetSku.getId()).orElseThrow();

      assertThat(previousDefaultSku.isDefault()).isFalse();
      assertThat(updatedDefaultSku.isDefault()).isTrue();
    }
  }

  @Nested
  @DisplayName("SKU 삭제 테스트")
  class DeleteSku {
    @Test
    @DisplayName("SKU 삭제 성공")
    void deleteSku_Success() {
      // given
      DeleteSkuCommand command =
          new DeleteSkuCommand(creatorId, dummyProduct.getId(), dummySku.get(1).getId());

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
