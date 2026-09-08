package com.sub9.orderservice.cart.presentation.request;

import com.sub9.orderservice.cart.application.dto.DeleteCartItemCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record DeleteCartItemRequest(
    @NotEmpty(message = "삭제할 상품을 선택해주세요.")
        List<@NotNull(message = "삭제할 장바구니 항목을 확인해주세요.") UUID> cartIds) {
  public DeleteCartItemCommand toCommand(UUID userId) {
    return new DeleteCartItemCommand(userId, cartIds);
  }
}
