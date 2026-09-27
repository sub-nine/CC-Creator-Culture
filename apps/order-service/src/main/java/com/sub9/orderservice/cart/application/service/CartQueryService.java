package com.sub9.orderservice.cart.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.cart.application.dto.CartItemInfo;
import com.sub9.orderservice.cart.application.dto.CartProductInfo;
import com.sub9.orderservice.cart.application.port.in.CartQueryUseCase;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.application.port.out.CartUserPort;
import com.sub9.orderservice.cart.application.dto.CreatorNameInfo;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.domain.repository.CartRepository;
import com.sub9.orderservice.cart.presentation.response.CartItemResponse;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Product와 User 호출 동안 DB 연결을 점유하지 않도록 조회마다 저장소의 짧은 트랜잭션만 사용한다.
@Service
@RequiredArgsConstructor
public class CartQueryService implements CartQueryUseCase {
  private final CartProductPort cartProductPort;
  private final CartRepository cartRepository;
  private final CartUserPort cartUserPort;

  @Override
  public List<CartItemResponse> getCart(UUID userId) {
    List<Cart> cartItems = cartRepository.findAllByUserId(userId);
    List<CartItemInfo> itemInfos = getCartItemInfos(cartItems, false);

    Set<UUID> creatorIds =
        itemInfos.stream()
            .map(CartItemInfo::creatorId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, String> creatorName =
        creatorIds.isEmpty()
            ? Map.of()
            : cartUserPort.getCreatorNamesByIds(List.copyOf(creatorIds)).stream()
                .collect(
                    Collectors.toMap(CreatorNameInfo::creatorId, CreatorNameInfo::creatorName));

    return itemInfos.stream()
        .map(info -> CartItemResponse.of(info, creatorName.get(info.creatorId())))
        .toList();
  }

  @Override
  public List<CartItemInfo> getCartItems(UUID customerId, List<UUID> cartItemIds) {
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
