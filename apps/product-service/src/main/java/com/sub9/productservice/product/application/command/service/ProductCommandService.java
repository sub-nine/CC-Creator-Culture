package com.sub9.productservice.product.application.command.service.product;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.kafka.event.ProductCreatedEvent;
import com.sub9.productservice.product.application.command.dto.product.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateProductCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateProductStatusCommand;
import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;
import com.sub9.productservice.product.application.command.dto.sku.CreateSkuCommand;
import com.sub9.productservice.product.application.port.in.image.ProductImageCommandUseCase;
import com.sub9.productservice.product.application.port.in.product.AdminProductStatusUseCase;
import com.sub9.productservice.product.application.port.in.product.ProductCommandUseCase;
import com.sub9.productservice.product.application.validation.SkuValidator;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import com.sub9.productservice.product.domain.repository.SkuCommandRepository;
import com.sub9.productservice.product.domain.repository.StockCommandRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductCommandService implements ProductCommandUseCase, AdminProductStatusUseCase {
  private final ProductCommandRepository productCommandRepository;
  private final SkuCommandRepository skuCommandRepository;
  private final StockCommandRepository stockCommandRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ProductImageCommandUseCase productImageCommandUseCase;

  @Override
  public UUID createProduct(CreateProductCommand command, List<UploadImageCommand> images) {
    SkuValidator.validateForCreate(command.skus());

    Product product = Product.create(command.creatorId(), command.name(), command.content());
    Product savedProduct = productCommandRepository.save(product);

    UUID productId = savedProduct.getId();

    boolean hasOneSku = command.skus().size() == 1;

    for (CreateSkuCommand skuCommand : command.skus()) {
      Sku sku =
          Sku.create(
              productId,
              skuCommand.name(),
              skuCommand.price(),
              hasOneSku || skuCommand.isDefault());

      skuCommandRepository.save(sku);

      Stock stock = Stock.create(sku.getId(), skuCommand.quantity());
      stockCommandRepository.save(stock);
    }

    productImageCommandUseCase.uploadImages(productId, images);

    // Category 생성 및 매핑 이벤트
    eventPublisher.publishEvent(
        new ProductCreatedEvent(
            productId,
            savedProduct.getCreatorId(),
            savedProduct.getName(),
            savedProduct.getContent(),
            command.hashTags()));

    return productId;
  }

  @Override
  public void updateProduct(UpdateProductCommand command) {
    Product product = findByProductId(command.productId());
    product.validateOwner(command.creatorId());
    product.update(command.name(), command.content());
  }

  @Override
  public void updateStatusProduct(UpdateProductStatusCommand command) {
    Product product = findByProductId(command.productId());
    if (command.isCreator()) {
      product.validateOwner(command.userId());
      product.updateStatusByCreator(command.productStatus());
      return;
    }
    product.updateStatusByAdmin(command.productStatus());
  }

  @Override
  public void deleteProduct(UUID creatorId, UUID productId) {
    Product product =
        productCommandRepository
            .findByIdForUpdate(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

    product.validateOwner(creatorId);
    product.delete(creatorId);

    List<Sku> skus = skuCommandRepository.findAllByProductIdAndDeletedAtIsNull(productId);

    for (Sku sku : skus) {
      sku.delete(creatorId);
    }
  }

  // ============================== Helper Method ====================================
  private Product findByProductId(UUID productId) {
    return productCommandRepository
        .findByIdAndDeletedAtIsNull(productId)
        .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
  }
}
