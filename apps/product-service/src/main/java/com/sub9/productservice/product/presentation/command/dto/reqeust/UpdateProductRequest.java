package com.sub9.productservice.product.presentation.command.dto.reqeust;

import com.sub9.productservice.product.application.command.dto.UpdateProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateProductRequest(
    @NotBlank(message = "상품명은 필수입니다.") @Size(max = 100, message = "상품명은 100자를 초과할 수 없습니다.")
        String name,
    @NotBlank(message = "상품 설명은 필수입니다.") @Size(max = 5000, message = "상품 설명은 5000자를 초과할 수 없습니다.")
        String content) {
  public UpdateProductCommand toCommand(UUID creatorId, UUID productId) {
    return new UpdateProductCommand(creatorId, productId, name, content);
  }
}
