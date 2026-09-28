package com.sub9.productservice.product.infrastructure.persistence.command.product;

import com.sub9.productservice.product.domain.model.ImageUpload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageUploadJpaRepository extends JpaRepository<ImageUpload, UUID> {
  List<ImageUpload> findAllByCreatedAtBefore(Instant cutoff);
}
