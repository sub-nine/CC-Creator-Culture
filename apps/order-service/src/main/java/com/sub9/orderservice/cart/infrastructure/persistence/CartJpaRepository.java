package com.sub9.orderservice.cart.infrastructure.persistence;

import com.sub9.orderservice.cart.domain.model.Cart;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CartJpaRepository extends JpaRepository<Cart, UUID> {
  List<Cart> findAllByUserIdAndIdIn(UUID userId, List<UUID> cartIds);

  int countByUserId(UUID userId);

  void deleteAllByUserIdAndIdIn(UUID userId, List<UUID> cartIds);

  Optional<Cart> findByIdAndUserId(UUID cartId, UUID userId);

  List<Cart> findAllByUserId(UUID userId);

  @Modifying
  @Query("DELETE FROM Cart c WHERE c.productId = :productId")
  void deleteAllByProductId(UUID productId);

  @Modifying
  @Query("DELETE FROM Cart c WHERE c.skuId = :skuId")
  void deleteAllBySkuId(UUID skuId);
}
