package com.sub9.productservice.product.application.port;

import com.sub9.common.kafka.event.ProductViewSyncEvent;

import java.util.concurrent.CompletableFuture;

public interface ProductViewCountPublisher {
  CompletableFuture<Void> publish(ProductViewSyncEvent event);
}
