package com.sub9.productservice.common.config.kafka;

import com.sub9.common.kafka.topic.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
  @Bean
  public NewTopic productImageUploadedTopic() {
    return TopicBuilder.name(KafkaTopics.PRODUCT_IMAGE_UPLOADED).partitions(3).replicas(1).build();
  }
}
