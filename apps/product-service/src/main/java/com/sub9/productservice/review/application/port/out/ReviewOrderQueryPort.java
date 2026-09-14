package com.sub9.productservice.review.application.port.out;

import com.sub9.productservice.review.application.port.out.dto.ProductPurchaseInfo;

import java.util.UUID;

public interface ReviewOrderQueryPort {
  ProductPurchaseInfo hasPurchasedProduct(UUID userId, UUID orderItemId);
}
