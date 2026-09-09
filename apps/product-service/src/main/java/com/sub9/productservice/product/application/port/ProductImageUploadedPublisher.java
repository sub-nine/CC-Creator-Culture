package com.sub9.productservice.product.application.port;

import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;

import java.util.concurrent.CompletableFuture;

public interface ProductImageUploadedPublisher {
  CompletableFuture<Void> publish(ProductImageUploadedEvent event);
}
