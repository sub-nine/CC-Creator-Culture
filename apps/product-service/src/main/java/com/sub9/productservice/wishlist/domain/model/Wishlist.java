package com.sub9.productservice.wishlist.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_wishlists",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_wishlist_user_id_product_id",
          columnNames = {"user_id", "product_id"})
    },
    indexes = {@Index(name = "idx_wishlist_product_id", columnList = "product_id")})
public class Wishlist {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private Instant createdAt;

  public static Wishlist create(UUID userId, UUID productId) {
    Wishlist wishlist = new Wishlist();
    wishlist.id = UuidCreator.getTimeOrderedEpoch();
    wishlist.userId = userId;
    wishlist.productId = productId;
    wishlist.createdAt = Instant.now();

    return wishlist;
  }
}
