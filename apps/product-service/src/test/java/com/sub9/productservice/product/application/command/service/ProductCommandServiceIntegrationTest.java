package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.CreateSkuCommand;
import com.sub9.productservice.product.application.command.dto.UpdateProductCommand;
import com.sub9.productservice.product.application.command.dto.UpdateProductStatusCommand;
import com.sub9.productservice.product.application.event.ProductCreatedEvent;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.infrastructure.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.command.stock.StockCommandJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@RecordApplicationEvents
@DisplayName("ProductCommandService - 통합 테스트")
class ProductCommandServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired ProductCommandService productCommandService;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired SkuCommandJpaRepository skuRepository;
  @Autowired StockCommandJpaRepository stockRepository;
  @Autowired ApplicationEvents applicationEvents;
  @Autowired EntityManager entityManager;

  private final UUID creatorId = UUID.randomUUID();
  private Product dummyProduct;

  @BeforeEach
  void setUp() {
    CreateProductCommand command =
        new CreateProductCommand(
            List.of("왁뿌볼", "말랑이"),
            creatorId,
            "말랑이",
            "말랑이 설명",
            List.of(new CreateSkuCommand("핑크", 10000L, true, 10)));

    Product product = Product.create(command.creatorId(), command.name(), command.content());

    dummyProduct = productRepository.save(product);

    Sku dummySku =
        Sku.create(
            dummyProduct.getId(),
            dummyProduct.getName(),
            command.skus().get(0).price(),
            command.skus().get(0).isDefault());

    skuRepository.save(dummySku);
  }

  @Nested
  @DisplayName("상품 등록 테스트")
  class CreateProductTest {
    @Test
    @DisplayName("유효한 상품 등록 명령을 실행하면 상품, SKU, 재고를 저장하고 생성 이벤트를 발행한다")
    void when_command_is_valid_create_product_saves_product_skus_stocks_and_publishes_event() {
      // given
      UUID creatorId = UUID.randomUUID();
      CreateProductCommand command =
          new CreateProductCommand(
              List.of("왁뿌볼", "말랑이"),
              creatorId,
              "왁뿌볼",
              "왁뿌볼 설명",
              List.of(
                  new CreateSkuCommand("핑크", 10000L, true, 10),
                  new CreateSkuCommand("불류", 15000L, false, 20)));

      // when
      productCommandService.createProduct(command);

      // then
      Product product =
          productRepository.findAll().stream()
              .filter(p -> p.getCreatorId().equals(creatorId))
              .findFirst()
              .orElseThrow();

      assertThat(product.getCreatorId()).isEqualTo(creatorId);
      assertThat(product.getName()).isEqualTo("왁뿌볼");
      assertThat(product.getContent()).isEqualTo("왁뿌볼 설명");
      assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
      assertThat(product.getViewCount()).isZero();
      assertThat(product.getReviewCount()).isZero();
      assertThat(product.getCreatedAt()).isNotNull();

      List<Sku> skus = skuRepository.findAllByProductIdAndDeletedAtIsNull(product.getId());

      Map<UUID, Stock> stocksBySkuId =
          stockRepository.findAll().stream()
              .filter(stock -> skus.stream().anyMatch(sku -> sku.getId().equals(stock.getSkuId())))
              .collect(Collectors.toMap(Stock::getSkuId, Function.identity()));

      assertThat(stocksBySkuId).hasSize(2);
      assertThat(stocksBySkuId.get(findSku(skus, "핑크").getId()).getQuantity()).isEqualTo(10);
      assertThat(stocksBySkuId.get(findSku(skus, "불류").getId()).getQuantity()).isEqualTo(20);

      assertThat(applicationEvents.stream(ProductCreatedEvent.class))
          .containsExactly(
              new ProductCreatedEvent(
                  product.getId(), creatorId, "왁뿌볼", "왁뿌볼 설명", List.of("왁뿌볼", "말랑이")));
    }

    private Sku findSku(List<Sku> skus, String name) {
      return skus.stream().filter(sku -> sku.getName().equals(name)).findFirst().orElseThrow();
    }
  }

  @Nested
  @DisplayName("상품 정보 수정 테스트")
  class UpdateProductTest {
    @Test
    @DisplayName("상품 정보 수정에 성공한다.")
    void updateProduct_success() {
      // given
      UpdateProductCommand command =
          new UpdateProductCommand(creatorId, dummyProduct.getId(), "수정된 상품명", "수정된 상품 설명");

      // when
      productCommandService.updateProduct(command);

      entityManager.flush();
      entityManager.clear();

      // then
      Product updatedProduct = productRepository.findById(dummyProduct.getId()).orElseThrow();

      assertThat(updatedProduct.getName()).isEqualTo(command.name());
      assertThat(updatedProduct.getContent()).isEqualTo(command.content());
    }
  }

  @Nested
  @DisplayName("상품 상태 수정 테스트")
  class UpdateStatusProductTest {
    @Test
    @DisplayName("창작자가 상품 상태를 INACTIVE로 수정하는 데 성공한다.")
    void updateStatusProduct_success_for_creator() {
      // given
      UpdateProductStatusCommand command =
          new UpdateProductStatusCommand(
              creatorId, dummyProduct.getId(), "CREATOR", ProductStatus.INACTIVE.name());

      // when
      productCommandService.updateStatusProduct(command);

      entityManager.flush();
      entityManager.clear();

      // then
      Product updatedProduct = productRepository.findById(dummyProduct.getId()).orElseThrow();

      assertThat(updatedProduct.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    @DisplayName("관리자가 상품 상태를 SUSPENDED로 수정하는 데 성공한다.")
    void updateStatusProduct_success_for_admin() {
      // given
      UpdateProductStatusCommand command =
          new UpdateProductStatusCommand(
              UUID.randomUUID(), dummyProduct.getId(), "MASTER", ProductStatus.SUSPENDED.name());

      // when
      productCommandService.updateStatusProduct(command);

      entityManager.flush();
      entityManager.clear();

      // then
      Product updatedProduct = productRepository.findById(dummyProduct.getId()).orElseThrow();

      assertThat(updatedProduct.getStatus()).isEqualTo(ProductStatus.SUSPENDED);
    }

    @Test
    @DisplayName("관리자가 SUSPENDED 상품을 ACTIVE로 수정하는 데 성공한다.")
    void updateStatusProduct_success_when_admin_activates_product() {
      // given
      dummyProduct.updateStatus(ProductStatus.SUSPENDED);
      entityManager.flush();

      UpdateProductStatusCommand command =
          new UpdateProductStatusCommand(
              UUID.randomUUID(), dummyProduct.getId(), "MASTER", ProductStatus.ACTIVE.name());

      // when
      productCommandService.updateStatusProduct(command);

      entityManager.flush();
      entityManager.clear();

      // then
      Product updatedProduct = productRepository.findById(dummyProduct.getId()).orElseThrow();

      assertThat(updatedProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }
  }

  @Nested
  @DisplayName("상품 삭제 테스트")
  class DeleteProduct {
    @Test
    @DisplayName("상품 삭제에 성공하면 상품, SKU를 삭제한다.")
    void deleteProduct_success() {
      // when
      productCommandService.deleteProduct(creatorId, dummyProduct.getId());

      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(productRepository.findByIdAndDeletedAtIsNull(dummyProduct.getId())).isEmpty();
      assertThat(skuRepository.findAllByProductIdAndDeletedAtIsNull(dummyProduct.getId()))
          .isEmpty();
    }
  }
}
