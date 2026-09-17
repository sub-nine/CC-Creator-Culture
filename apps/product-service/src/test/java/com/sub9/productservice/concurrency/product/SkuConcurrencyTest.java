package com.sub9.productservice.concurrency.product;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.sub9.productservice.product.application.command.dto.sku.AddSkuCommand;
import com.sub9.productservice.product.application.command.dto.sku.UpdateSkuCommand;
import com.sub9.productservice.product.application.port.in.sku.SkuCommandUseCase;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ProductCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.sku.SkuCommandJpaRepository;
import com.sub9.productservice.product.infrastructure.persistence.command.stock.StockHistoryCommandJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import com.sub9.productservice.support.ConcurrencyTestingUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("SkuCommandService - 동시성 테스트")
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
public class SkuConcurrencyTest extends AbstractIntegrationTest {
  @Autowired StockHistoryCommandJpaRepository stockHistoryRepository;
  @Autowired ProductCommandJpaRepository productRepository;
  @Autowired SkuCommandJpaRepository skuRepository;
  @Autowired SkuCommandUseCase skuCommandUseCase;

  private final UUID creatorId = UUID.randomUUID();
  private UUID productId;

  @BeforeEach
  void setUp() {
    Product product = productRepository.save(Product.create(creatorId, "말랑이", "설명"));
    skuRepository.save(Sku.create(product.getId(), "핑크 | M", 10000L, true));
    productId = product.getId();
  }

  @AfterEach
  void tearDown() {
    stockHistoryRepository.deleteAllInBatch();
    skuRepository.deleteAllInBatch();
    productRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("대표 SKU를 동시에 등록해도 대표 SKU는 하나만 유지한다.")
  void addSku_success_when_default_skus_are_added_concurrently() throws Exception {
    // given
    int threadCount = 3;

    // when
    ConcurrencyTestingUtil.run(
        threadCount,
        () ->
            skuCommandUseCase.addSku(
                new AddSkuCommand(productId, creatorId, "옵션", 10000L, true, 10)));

    // then
    var skus = skuRepository.findAll();
    assertThat(skus).filteredOn(Sku::isDefault).hasSize(1);
    assertThat(skus).hasSize(4);
  }

  @Test
  @DisplayName("대표가 아닌 서로 다른 SKU를 동시에 대표로 변경해도 최종적으로 대표 SKU는 하나여야 한다.")
  void updateSku_success_when_default_skus_are_changed_concurrently() throws Exception {
    // given
    AtomicInteger index = new AtomicInteger();

    List<Sku> updateSkus =
        List.of(
            Sku.create(productId, "1번", 10000L, false), Sku.create(productId, "2번", 10000L, false));

    skuRepository.saveAll(updateSkus);

    // when
    ConcurrencyTestingUtil.run(
        updateSkus.size(),
        () -> {
          Sku sku = updateSkus.get(index.getAndIncrement());

          skuCommandUseCase.updateSku(
              new UpdateSkuCommand(
                  creatorId, productId, sku.getId(), sku.getName(), sku.getPrice(), true));
        });

    // then
    var skus = skuRepository.findAllByProductIdAndDeletedAtIsNull(productId);
    assertThat(skus).filteredOn(Sku::isDefault).hasSize(1);
  }
}
