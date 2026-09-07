package com.sub9.productservice.product.domain.model;

public enum StockHistoryReason {
  ORDER,
  ORDER_CREATION_FAILED,
  ORDER_EXPIRED,
  ORDER_CANCEL,
  PAYMENT_FAILED,

  CREATOR_ADJUSTMENT
}
