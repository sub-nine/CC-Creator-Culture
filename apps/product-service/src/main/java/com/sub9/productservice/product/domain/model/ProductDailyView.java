package com.sub9.productservice.product.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "p_product_daily_views",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_product_datily_view_product_id_view_date",
          columnNames = {"product_id, view_date"})
    })
public class ProductDailyView {
  @Id UUID id;

  @Column(nullable = false)
  UUID productId;

  @Column(nullable = false)
  long viewCount;

  @Column(nullable = false)
  LocalDate viewDate;

  @PrePersist
  protected void onCreate() {
    if (this.id == null) {
      this.id = UuidCreator.getTimeOrderedEpoch();
    }
  }
}
