package com.sub9.productservice.product.application.event;

import com.sub9.productservice.product.application.port.out.image.ProductImageUploadedEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductImageUploadedEventListener {
  private final ProductImageUploadedEventPort imageUploadedPublisher;

  @TransactionalEventListener
  public void handleProductImageUploadedEvent(ProductImageUploadedEvent event) {
    imageUploadedPublisher.publish(event);
  }
}
