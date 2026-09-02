package com.sub9.productservice.config.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka")
public record KafkaProperties(
    String bootstrapServers,
    String autoOffsetReset,
    String productGroupId,
    String categoryGroupId) {}
