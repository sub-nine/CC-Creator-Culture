package com.sub9.productservice.product.infrastructure.command.sku;

import com.sub9.productservice.product.domain.model.Sku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SkuCommandJpaRepository extends JpaRepository<Sku, UUID> {}
