package com.sub9.productservice.product.application.command.dto;

import com.sub9.productservice.product.domain.model.ProductStatus;
import java.util.UUID;

public record UpdateProductStatusCommand(UUID userId, UUID productId, String role, String status) {
  public ProductStatus productStatus() {
    return ProductStatus.valueOf(status);
  }

  public boolean isCreator() {
    return "CREATOR".equals(role);
  }
}
