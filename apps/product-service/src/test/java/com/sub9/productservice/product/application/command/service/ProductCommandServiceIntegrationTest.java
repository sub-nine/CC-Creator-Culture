package com.sub9.productservice.product.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.CreateSkuCommand;
import com.sub9.productservice.product.application.event.ProductCreatedEvent;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.infrastructure.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.command.stock.StockCommandJpaRepository;
import com.sub9.productservice.product.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@RecordApplicationEvents
@DisplayName("ProductCommandService - 통합 테스트")
class ProductCommandServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired ProductCommandService productCommandService;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired SkuCommandJpaRepository skuRepository;
  @Autowired StockCommandJpaRepository stockRepository;
  @Autowired ApplicationEvents applicationEvents;

  @Nested
  @DisplayName("상품 등록 테스트")
  class CreateProduct {

    @Test
    @Transactional
    @Rollback
    @DisplayName("유효한 상품 등록 명령을 실행하면 상품, SKU, 재고를 저장하고 생성 이벤트를 발행한다")
    void when_command_is_valid_create_product_saves_product_skus_stocks_and_publishes_event() {
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

      productCommandService.createProduct(command);

      List<Product> products = productRepository.findAll();
      assertThat(products).hasSize(1);

      Product product = products.getFirst();
      assertThat(product.getCreatorId()).isEqualTo(creatorId);
      assertThat(product.getName()).isEqualTo("왁뿌볼");
      assertThat(product.getContent()).isEqualTo("왁뿌볼 설명");
      assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
      assertThat(product.getViewCount()).isZero();
      assertThat(product.getReviewCount()).isZero();
      assertThat(product.getCreatedAt()).isNotNull();

      List<Sku> skus = skuRepository.findAll();
      assertThat(skus).hasSize(2).allMatch(sku -> sku.getProductId().equals(product.getId()));
      assertThat(skus)
          .extracting(Sku::getName, Sku::getPrice, Sku::isDefault)
          .containsExactlyInAnyOrder(
              org.assertj.core.groups.Tuple.tuple("핑크", 10_000L, true),
              org.assertj.core.groups.Tuple.tuple("불류", 15_000L, false));

      Map<UUID, Stock> stocksBySkuId =
          stockRepository.findAll().stream()
              .collect(Collectors.toMap(Stock::getSkuId, Function.identity()));

      assertThat(stocksBySkuId).hasSize(2);
      assertThat(stocksBySkuId.get(findSku(skus, "핑크").getId()).getQuantity()).isEqualTo(10);
      assertThat(stocksBySkuId.get(findSku(skus, "불류").getId()).getQuantity()).isEqualTo(20);

      assertThat(applicationEvents.stream(ProductCreatedEvent.class))
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.productId()).isEqualTo(product.getId());
                assertThat(event.creatorId()).isEqualTo(creatorId);
                assertThat(event.name()).isEqualTo("왁뿌볼");
                assertThat(event.content()).isEqualTo("왁뿌볼 설명");
                assertThat(event.hashTags()).containsExactly("왁뿌볼", "말랑이");
              });
    }

    private Sku findSku(List<Sku> skus, String name) {
      return skus.stream().filter(sku -> sku.getName().equals(name)).findFirst().orElseThrow();
    }
  }
}
