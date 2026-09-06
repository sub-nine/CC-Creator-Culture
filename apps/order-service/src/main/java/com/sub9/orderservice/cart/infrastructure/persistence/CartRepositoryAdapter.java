package com.sub9.orderservice.cart.infrastructure.persistence;

import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.domain.repository.CartRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartRepositoryAdapter implements CartRepository {
  private final CartJpaRepository jpaRepository;

  @Override
  public Cart saveAndFlush(Cart cart) {
    return jpaRepository.saveAndFlush(cart);
  }

  @Override
  public List<Cart> findAllByUserIdAndIdIn(UUID customerId, List<UUID> cartItemIds) {
    return jpaRepository.findAllByUserIdAndIdIn(customerId, cartItemIds);
  }

  @Override
  public int countByUserId(UUID userId) {
    return jpaRepository.countByUserId(userId);
  }
}
