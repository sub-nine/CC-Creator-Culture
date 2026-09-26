package com.sub9.productservice.product.application.port.in.image;

import java.time.Instant;

public interface ProductImageCleanupUseCase {
  void deleteExpiredImages(Instant now);

  void deleteExpiredImageUploads(Instant now);
}
