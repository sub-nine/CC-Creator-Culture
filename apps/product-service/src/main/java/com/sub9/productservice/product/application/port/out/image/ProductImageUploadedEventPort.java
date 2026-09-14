package com.sub9.productservice.product.application.port.out.image;

import com.sub9.productservice.product.application.event.ProductImageUploadedEvent;

import java.util.concurrent.CompletableFuture;

public interface ProductImageUploadedEventPort {
  CompletableFuture<Void> publish(ProductImageUploadedEvent event);
}
