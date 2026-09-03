package com.sub9.productservice.product.infrastructure.command.sku;

import com.sub9.productservice.product.domain.model.Sku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkuCommandJpaRepository extends JpaRepository<Sku, UUID> {
    List<Sku> findAllByProductIdAndDeletedAtIsNull(UUID productId);

    Optional<Sku> findByIdAndProductIdAndDeletedAtIsNull(UUID skuId, UUID productId);

    long countByProductIdAndDeletedAtIsNull(UUID productId);
}
