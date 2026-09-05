package com.sub9.productservice.product.presentation.command.dto.reqeust;

import com.sub9.productservice.product.application.command.dto.UpdateSkuCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateSkuRequest(
    @NotBlank(message = "옵션명은 필수입니다.") @Size(max = 25, message = "옵션명은 25자를 초과할 수 없습니다.")
        String name,
    @NotNull(message = "가격은 필수입니다.") @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.") Long price,
    boolean isDefault) {
  public UpdateSkuCommand toCommand(UUID creatorId, UUID productId, UUID skuId) {
    return new UpdateSkuCommand(creatorId, productId, skuId, name, price, isDefault);
  }
}
