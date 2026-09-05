package com.sub9.productservice.product.infrastructure.persistence.command.product;

import com.sub9.productservice.product.domain.model.ProductDailyView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductDailyViewCommandJPARepository
    extends JpaRepository<ProductDailyView, UUID> {
  @Modifying
  @Query(
      value =
          """
                  INSERT INTO p_product_daily_views (
                      id,
                      product_id,
                      view_count,
                      view_date
                  )
                  VALUES (
                      :id,
                      :productId,
                      :viewCount,
                      :viewDate
                  )
                  ON CONFLICT (product_id, view_date)
                  DO UPDATE SET
                      view_count = p_product_daily_views.view_count + EXCLUDED.view_count
                  """,
      nativeQuery = true)
  void upsert(
      @Param("id") UUID id,
      @Param("productId") UUID productId,
      @Param("viewCount") long viewCount,
      @Param("viewDate") LocalDate viewDate);

  List<ProductDailyView> findAllByViewDate(LocalDate viewDate);
}
