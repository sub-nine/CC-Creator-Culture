package com.sub9.orderservice.cart.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.domain.repository.CartRepository;
import com.sub9.orderservice.cart.presentation.response.CartItemResponse;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartQueryService {
  private final CartRepository cartRepository;
  private final CartProductPort cartProductPort;

  public List<CartItemResponse> getCart(UUID userId) {
    List<Cart> cartItems = cartRepository.findAllByUserId(userId);

    return getCartItemInfos(cartItems, false).stream().map(CartItemResponse::from).toList();
  }

  public List<CartItemInfo> getCartItems(
      UUID customerId, List<UUID> cartItemIds) {
    List<Cart> carts = cartRepository.findAllByUserIdAndIdIn(customerId, cartItemIds);

    return getCartItemInfos(carts, true);
  }

  private List<CartItemInfo> getCartItemInfos(List<Cart> carts, boolean forOrder) {
    if (carts.isEmpty()) {
      return List.of();
    }

    List<UUID> skuIds = carts.stream().map(Cart::getSkuId).toList();

    Map<UUID, CartProductInfo> skuInfoMap =
        cartProductPort.getCartItemProducts(skuIds).stream()
            .collect(Collectors.toMap(CartProductInfo::skuId, Function.identity()));

    if (forOrder && carts.stream().anyMatch(cart -> !skuInfoMap.containsKey(cart.getSkuId()))) {
      throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
    }

    return carts.stream()
        .filter(cart -> skuInfoMap.containsKey(cart.getSkuId()))
        .map(cart -> toCartItemInfo(cart, skuInfoMap))
        .toList();
  }

  private CartItemInfo toCartItemInfo(Cart cart, Map<UUID, CartProductInfo> productBySkuId) {
    CartProductInfo product = productBySkuId.get(cart.getSkuId());

    if (product == null) {
      throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
    }

    return new CartItemInfo(
        cart.getId(),
        cart.getSkuId(),
        product.productId(),
        product.creatorId(),
        product.productName(),
        product.skuName(),
        product.productStatus(),
        product.price(),
        cart.getQuantity());
  }
}
