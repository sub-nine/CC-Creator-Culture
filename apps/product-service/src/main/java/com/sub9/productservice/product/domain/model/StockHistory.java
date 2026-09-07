package com.sub9.productservice.product.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@Entity
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_stock_history",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_stock_history_sku_order_reason",
          columnNames = {"sku_id", "order_id", "reason"})
    })
public class StockHistory {
  @Id private UUID id;

  private UUID orderId;

  @Column(nullable = false)
  private UUID skuId;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private StockHistoryReason reason;

  @Column(nullable = false)
  private Instant createdAt;

  public static StockHistory create(
      UUID orderId, UUID skuId, int quantity, StockHistoryReason reason) {
    StockHistory stockHistory = new StockHistory();
    stockHistory.id = UuidCreator.getTimeOrderedEpoch();
    stockHistory.orderId = orderId;
    stockHistory.skuId = skuId;
    stockHistory.quantity = quantity;
    stockHistory.reason = reason;
    stockHistory.createdAt = Instant.now();

    return stockHistory;
  }
}
