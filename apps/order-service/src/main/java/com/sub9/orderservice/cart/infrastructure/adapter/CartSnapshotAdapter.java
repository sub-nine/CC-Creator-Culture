package com.sub9.orderservice.cart.infrastructure.adapter;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CartSnapshotAdapter implements CartSnapshotPort {
  private final CartQueryService cartQueryService;

  @Override
  public List<CartSnapshotPort.CartItemSnapshot> getCartItems(
      UUID customerId, List<UUID> cartItemIds) {
    List<CartItemInfo> items = cartQueryService.getCartItems(customerId, cartItemIds);

    if (items.size() != cartItemIds.size()) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
    }

    return items.stream().map(this::toSnapshot).toList();
  }

  private CartSnapshotPort.CartItemSnapshot toSnapshot(CartItemInfo item) {
    return new CartSnapshotPort.CartItemSnapshot(
        item.cartId(),
        item.creatorId(),
        item.productId(),
        item.skuId(),
        item.productName(),
        item.skuName(),
        item.price(),
        item.quantity());
  }
}
