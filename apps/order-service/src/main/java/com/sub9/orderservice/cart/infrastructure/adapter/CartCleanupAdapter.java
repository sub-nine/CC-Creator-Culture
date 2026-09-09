package com.sub9.orderservice.cart.infrastructure.adapter;

import com.sub9.orderservice.cart.application.dto.DeleteCartItemCommand;
import com.sub9.orderservice.cart.application.service.CartCommandService;
import com.sub9.orderservice.order.application.port.output.CartCleanupCommand;
import com.sub9.orderservice.order.application.port.output.CartCleanupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartCleanupAdapter implements CartCleanupPort {
  private final CartCommandService cartCommandService;

  @Override
  public void cleanup(CartCleanupCommand command) {
    cartCommandService.removeCartItem(toDeleteCartItemCommand(command));
  }

  private DeleteCartItemCommand toDeleteCartItemCommand(CartCleanupCommand cartCleanupCommand) {
    return new DeleteCartItemCommand(cartCleanupCommand.customerId(), cartCleanupCommand.cartItemIds());
  }
}
