package com.sub9.productservice.product.infrastructure.command.stock;

import com.sub9.productservice.product.domain.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface StockCommandJpaRepository extends JpaRepository<Stock, UUID> {

  @Modifying
  @Query(
      """
                        UPDATE Stock s
                        SET s.quantity = s.quantity + :quantity
                        WHERE s.skuId = :skuId
                    """)
  boolean increaseStock(UUID skuId, int quantity);

  @Modifying
  @Query(
      """
                    UPDATE Stock s
                    SET s.quantity = s.quantity - :quantity
                    WHERE s.skuId = :skuId
                    AND s.quantity >= :quantity
                    """)
  boolean decreaseStock(UUID skuId, int quantity);
}
