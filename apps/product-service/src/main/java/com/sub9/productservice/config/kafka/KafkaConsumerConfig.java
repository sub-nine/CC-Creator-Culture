package com.sub9.productservice.config.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {
  private final KafkaProperties kafkaProperties;

  @Bean
  public ConsumerFactory<String, String> consumerFactory() {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.autoOffsetReset());
    return new DefaultKafkaConsumerFactory<>(config);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      DefaultErrorHandler errorHandler, JsonMapper jsonMapper) {
    ConcurrentKafkaListenerContainerFactory<String, String> listener =
        new ConcurrentKafkaListenerContainerFactory<>();
    listener.setConsumerFactory(consumerFactory());
    listener.setCommonErrorHandler(errorHandler);
    listener.setRecordMessageConverter(new StringJacksonJsonMessageConverter(jsonMapper));
    listener.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    return listener;
  }

  @Bean
  public DefaultErrorHandler defaultErrorHandler() {
    // 메시지 처리 실패 시 2초 간격 3번 재시도
    FixedBackOff backOff = new FixedBackOff(2000L, 3L);

    // TODO : 추후 DLQ 구현

    return new DefaultErrorHandler(backOff);
  }
}
