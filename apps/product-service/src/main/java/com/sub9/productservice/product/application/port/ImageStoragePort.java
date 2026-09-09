package com.sub9.productservice.product.application.port;

import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;

public interface ImageStoragePort {
  String upload(String objectKey, ImageData imageData);

  ImageData download(String objectKey);

  void delete(String objectKey);
}
