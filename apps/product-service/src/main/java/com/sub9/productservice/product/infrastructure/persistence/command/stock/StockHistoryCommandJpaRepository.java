package com.sub9.productservice.product.infrastructure.persistence.command.stock;

import com.sub9.productservice.product.domain.model.StockHistory;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StockHistoryCommandJpaRepository extends JpaRepository<StockHistory, UUID> {
  @Modifying
  @Query(
      value =
          """
              INSERT INTO p_stock_history (
                  id,
                  order_id,
                  sku_id,
                  quantity,
                  reason,
                  created_at
              )
              VALUES (
                  :id,
                  :orderId,
                  :skuId,
                  :quantity,
                  :reason,
                  :createdAt
              )
              ON CONFLICT (sku_id, order_id, reason)
              DO NOTHING
              """,
      nativeQuery = true)
  int insertIfAbsent(
      UUID id, UUID orderId, UUID skuId, int quantity, String reason, Instant createdAt);
}
