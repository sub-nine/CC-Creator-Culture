package com.sub9.productservice.product.application.port.out.image;

import java.time.Duration;

public interface ImageStoragePort {
  String createPresignedPutUrl(String objectKey, String contentType, Duration expiration);

  long getContentLength(String objectKey);

  String upload(String objectKey, ImageData imageData);

  ImageData download(String objectKey);

  void delete(String objectKey);
}
