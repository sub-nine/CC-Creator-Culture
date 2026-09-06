package com.sub9.orderservice.cart.domain.repository;

import com.sub9.orderservice.cart.domain.model.Cart;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository {
  Cart saveAndFlush(Cart cart);

  List<Cart> findAllByUserIdAndIdIn(UUID customerId, List<UUID> cartIds);

  int countByUserId(UUID userId);

  void deleteAllByUserIdAndIdIn(UUID userId, List<UUID> cartIds);

  Optional<Cart> findByIdAndUserId(UUID uuid, UUID uuid1);
}
