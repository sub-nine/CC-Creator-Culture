package com.sub9.productservice.common.config.r2;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudflare.r2")
public record R2Properties(
    String accessKey,
    String secretKey,
    String endpoint,
    String bucket
) {}
