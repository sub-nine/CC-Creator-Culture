package com.sub9.productservice.product.application.port.in.image;

import java.util.UUID;

public interface ProductImageProcessingUseCase {
  public void resizeImage(UUID imageId, UUID productId, String originalKey);
}
