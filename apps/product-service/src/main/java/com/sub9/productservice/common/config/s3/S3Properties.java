package com.sub9.productservice.common.config.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(String region, String bucket, String publicUrl) {}
