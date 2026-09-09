package com.sub9.common.kafka.topic;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {
  public static final String PRODUCT_CREATED = "product.created";
  public static final String PRODUCT_VIEW_COUNT_SYNC = "product.view-count.sync";
  public static final String PRODUCT_IMAGE_UPLOADED = "product.image.uploaded";
  public static final String ORDER_PAID = "order_paid";
  public static final String ORDER_NOTIFICATION = "order.notification";
  public static final String STOCK_RESTORE = "stock.restore";
  public static final String HASHTAG_CREATED = "hashtag.created";
}
