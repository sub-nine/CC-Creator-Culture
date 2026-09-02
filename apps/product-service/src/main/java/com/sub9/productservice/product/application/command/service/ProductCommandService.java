package com.sub9.productservice.product.application.command.service;

import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.CreateSkuCommand;
import com.sub9.productservice.product.application.event.ProductCreatedEvent;
import com.sub9.productservice.product.application.validation.SkuValidator;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.model.Stock;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import com.sub9.productservice.product.domain.repository.SkuCommandRepository;
import com.sub9.productservice.product.domain.repository.StockCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductCommandService {
  private final ProductCommandRepository productCommandRepository;
  private final SkuCommandRepository skuCommandRepository;
  private final StockCommandRepository stockCommandRepository;
  private final ApplicationEventPublisher eventPublisher;

  public void createProduct(CreateProductCommand command) {
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

    // TODO : 이미지 등록 기능은 추후 추가 09.07 ~ 09.08 예정

    // Category 생성 및 매핑 이벤트
    eventPublisher.publishEvent(
        new ProductCreatedEvent(
            productId,
            savedProduct.getCreatorId(),
            savedProduct.getName(),
            savedProduct.getContent(),
            command.hashTags()));
  }
}
