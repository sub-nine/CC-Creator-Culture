package com.sub9.productservice.product.presentation.command.dto.product;

import com.sub9.productservice.product.application.command.dto.product.UpdateImageSortOrderCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateImageSortOrderRequest(@NotEmpty @Size(max = 5) List<UUID> imageIds) {
  public UpdateImageSortOrderCommand toCommand(UUID productId, UUID creatorId) {
    return new UpdateImageSortOrderCommand(productId, creatorId, imageIds);
  }
}
