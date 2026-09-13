package com.sub9.productservice.product.infrastructure.adapter;

import com.sub9.productservice.product.application.port.out.product.ProductQueryRepository;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.wishlist.application.port.out.WishlistProductPort;
import com.sub9.productservice.wishlist.application.query.dto.WishlistInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WishlistProductAdapter implements WishlistProductPort {
  private final ProductQueryRepository productQueryRepository;

  @Override
  public boolean existsByProductId(UUID productId) {
    return productQueryRepository.existsById(productId);
  }

  @Override
  public Map<UUID, WishlistInfo> findAllByProductIds(Set<UUID> productIds) {
    if (productIds.isEmpty()) return Map.of();

    return productQueryRepository.findProductsByIds(List.copyOf(productIds)).stream()
        .collect(
            Collectors.toMap(
                ProductInfo::productId,
                product ->
                    new WishlistInfo(
                        null,
                        product.productId(),
                        product.name(),
                        product.status().name(),
                        product.price(),
                        product.imageKey())));
  }
}
