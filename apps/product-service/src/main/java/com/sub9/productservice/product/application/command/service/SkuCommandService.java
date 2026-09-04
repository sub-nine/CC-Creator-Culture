package com.sub9.productservice.product.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.dto.UpdateSkuCommand;
import com.sub9.productservice.product.application.validation.SkuValidator;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.Sku;
import com.sub9.productservice.product.domain.repository.ProductCommandRepository;
import com.sub9.productservice.product.domain.repository.SkuCommandRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SkuCommandService {
  private final ProductCommandRepository productCommandRepository;
  private final SkuCommandRepository skuCommandRepository;

  public void updateSku(UpdateSkuCommand command) {
    Product product = findByProductId(command.productId());
    product.validateOwner(command.creatorId());

    Sku sku = findBySkuIdAndProductId(command.skuId(), command.productId());

    SkuValidator.validateForUpdate(sku.isDefault(), command.isDefault());

    if (command.isDefault() && !sku.isDefault()) {
      Sku defaultSku =
          skuCommandRepository
              .findByProductIdAndIsDefaultTrue(command.productId())
              .orElseThrow(() -> new BusinessException(ProductErrorCode.DEFAULT_SKU_NOT_FOUND));

      defaultSku.unsetDefault();
    }
    sku.update(command.name(), command.price(), command.isDefault());
  }

  public void deleteSku(DeleteSkuCommand command) {
    Product product = findByProductId(command.productId());
    product.validateOwner(command.creatorId());

    Sku sku = findBySkuIdAndProductId(command.skuId(), command.productId());

    long activeSkuCount =
        skuCommandRepository.countByProductIdAndDeletedAtIsNull(command.productId());

    SkuValidator.validateForDelete(sku.isDefault(), activeSkuCount);

    sku.delete(command.creatorId());

    // TODO : 이미지 등록 기능 추가 시 삭제 추가 예정 09.07 ~ 09.08
  }

  // ============================== Helper Method ====================================
  private Product findByProductId(UUID productId) {
    return productCommandRepository
        .findByIdForUpdate(productId)
        .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
  }

  private Sku findBySkuIdAndProductId(UUID skuId, UUID productId) {
    return skuCommandRepository
        .findByIdAndProductIdAndDeletedAtIsNull(skuId, productId)
        .orElseThrow(() -> new BusinessException(ProductErrorCode.SKU_NOT_FOUND));
  }
}
