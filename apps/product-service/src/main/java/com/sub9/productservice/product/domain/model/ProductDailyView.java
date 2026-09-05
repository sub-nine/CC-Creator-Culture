package com.sub9.productservice.product.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(
    name = "p_product_daily_views",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_product_daily_view_product_id_view_date",
          columnNames = {"product_id", "view_date"})
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDailyView {
  @Id UUID id;

  @Column(nullable = false)
  UUID productId;

  @Column(nullable = false)
  long viewCount;

  @Column(nullable = false)
  LocalDate viewDate;
}
