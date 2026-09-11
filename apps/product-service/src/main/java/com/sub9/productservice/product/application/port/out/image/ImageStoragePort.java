package com.sub9.productservice.product.application.port.out;


public interface ImageStoragePort {
  String upload(String objectKey, ImageData imageData);

  ImageData download(String objectKey);

  void delete(String objectKey);
}
