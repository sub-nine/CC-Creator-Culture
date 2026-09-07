package com.sub9.orderservice.cart.infrastructure.persistence;

import com.sub9.orderservice.cart.domain.model.Cart;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartJpaRepository extends JpaRepository<Cart, UUID> {
  List<Cart> findAllByUserIdAndIdIn(UUID userId, List<UUID> cartIds);

  int countByUserId(UUID userId);

  void deleteAllByUserIdAndIdIn(UUID userId, List<UUID> cartIds);

  Optional<Cart> findByIdAndUserId(UUID cartId, UUID userId);

  List<Cart> findAllByUserId(UUID userId);
}
