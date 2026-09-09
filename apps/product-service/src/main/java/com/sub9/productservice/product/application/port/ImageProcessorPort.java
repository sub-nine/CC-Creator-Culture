package com.sub9.productservice.product.application.port;

import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;

public interface ImageProcessorPort {
  public ImageData resize(ImageData imageData);
}
