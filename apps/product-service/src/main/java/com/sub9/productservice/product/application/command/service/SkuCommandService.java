package com.sub9.productservice.product.application.command.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.kafka.event.SkuDeletedEvent;
import com.sub9.productservice.product.application.command.dto.sku.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.dto.sku.UpdateSkuCommand;
import com.sub9.productservice.product.application.port.in.sku.SkuCommandUseCase;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.exception.SkuErrorCode;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.repository.ProductRepository;
import com.sub9.productservice.product.domain.repository.SkuRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SkuCommandService implements SkuCommandUseCase {
  private final ApplicationEventPublisher eventPublisher;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;

  @Override
  public void updateSku(UpdateSkuCommand command) {
    Product product = findByProductId(command.productId());
    product.validateOwner(command.creatorId());

    Sku sku = findBySkuIdAndProductId(command.skuId(), command.productId());

    if (command.isDefault() && !sku.isDefault()) {
      Sku defaultSku =
          skuRepository
              .findByProductIdAndIsDefaultTrue(command.productId())
              .orElseThrow(() -> new BusinessException(SkuErrorCode.DEFAULT_SKU_NOT_FOUND));

      defaultSku.unsetDefault();
    }
    sku.update(command.name(), command.price(), command.isDefault());
  }

  @Override
  public void deleteSku(DeleteSkuCommand command) {
    Product product = findByProductId(command.productId());
    product.validateOwner(command.creatorId());

    Sku sku = findBySkuIdAndProductId(command.skuId(), command.productId());

    long activeSkuCount = skuRepository.countByProductIdAndDeletedAtIsNull(command.productId());

    sku.deleteOption(command.creatorId(), activeSkuCount);

    eventPublisher.publishEvent(
        new SkuDeletedEvent(UuidCreator.getTimeOrderedEpoch(), sku.getId(), Instant.now()));
  }

  // ============================== Helper Method ====================================
  private Product findByProductId(UUID productId) {
    return productRepository
        .findByIdForUpdate(productId)
        .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
  }

  private Sku findBySkuIdAndProductId(UUID skuId, UUID productId) {
    return skuRepository
        .findByIdAndProductIdAndDeletedAtIsNull(skuId, productId)
        .orElseThrow(() -> new BusinessException(SkuErrorCode.SKU_NOT_FOUND));
  }
}
