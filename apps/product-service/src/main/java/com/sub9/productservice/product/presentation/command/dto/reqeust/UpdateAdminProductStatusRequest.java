package com.sub9.productservice.product.presentation.command.dto.reqeust;

import com.sub9.productservice.product.application.command.dto.UpdateProductStatusCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateAdminProductStatusRequest(
    @NotNull(message = "상품 상태는 필수 입력 값입니다.") Status status) {
  public enum Status {
    ACTIVE,
    SUSPENDED
  }

  public UpdateProductStatusCommand toCommand(UUID adminId, UUID productId, String role) {
    return new UpdateProductStatusCommand(adminId, productId, role, status.name());
  }
}
