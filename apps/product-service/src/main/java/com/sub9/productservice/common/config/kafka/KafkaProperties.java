package com.sub9.productservice.common.config.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka")
public record KafkaProperties(
    String bootstrapServers,
    String autoOffsetReset) {}
