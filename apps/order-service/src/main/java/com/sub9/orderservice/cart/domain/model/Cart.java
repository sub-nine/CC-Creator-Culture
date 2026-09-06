package com.sub9.orderservice.cart.domain.model;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_carts",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_carts_user_id_sku_id",
          columnNames = {"user_id", "sku_id"})
    },
    indexes = {@Index(name = "idx_carts_user_id", columnList = "user_id")})
public class Cart {
  @Id UUID id;

  @Column(nullable = false)
  UUID userId;

  @Column(nullable = false)
  UUID skuId;

  @Column(nullable = false)
  int quantity;

  public static Cart create(UUID id, UUID userId, UUID skuId, int quantity) {
    Cart cart = new Cart();
    cart.id = id;
    cart.userId = userId;
    cart.skuId = skuId;
    cart.quantity = quantity;

    return cart;
  }

  public void changeQuantity(int quantity) {
    this.quantity = quantity;
  }
}
