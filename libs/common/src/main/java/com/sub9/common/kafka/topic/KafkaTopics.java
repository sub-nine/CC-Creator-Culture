package com.sub9.common.kafka.topic;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {
  public static final String PRODUCT_CREATED = "product.created";
  public static final String PRODUCT_VIEW_COUNT_SYNC = "product.view-count.sync";
  public static final String ORDER_PAID = "order.paid";
}
