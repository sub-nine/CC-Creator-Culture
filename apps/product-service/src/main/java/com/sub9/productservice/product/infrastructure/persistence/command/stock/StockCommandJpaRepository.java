package com.sub9.productservice.product.infrastructure.persistence.command.stock;

import com.sub9.productservice.product.domain.model.Stock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StockCommandJpaRepository extends JpaRepository<Stock, UUID> {

  @Modifying
  @Query(
      """
       UPDATE Stock s
       SET s.quantity = s.quantity + :quantity
       WHERE s.skuId = :skuId
       """)
  int increaseStock(UUID skuId, int quantity);

  @Modifying
  @Query(
      """
      UPDATE Stock s
      SET s.quantity = s.quantity - :quantity
      WHERE s.skuId = :skuId
      AND s.quantity >= :quantity
       """)
  int decreaseStock(UUID skuId, int quantity);

  @Modifying
  @Query(
      """
      UPDATE Stock s
      SET s.quantity = s.quantity + :quantity
      WHERE s.skuId = :skuId
      AND s.quantity + :quantity >= 0
      """)
  int adjustStock(UUID skuId, int quantity);
}
