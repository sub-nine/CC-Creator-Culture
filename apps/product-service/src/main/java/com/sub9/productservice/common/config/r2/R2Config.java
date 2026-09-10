package com.sub9.productservice.common.config.r2;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(R2Properties.class)
public class R2Config {
  private final R2Properties r2Properties;

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .endpointOverride(URI.create(r2Properties.endpoint()))
        .region(Region.of("auto"))
        .credentialsProvider(credentialsProvider())
        .serviceConfiguration(s3Configuration())
        .build();
  }

  private AwsCredentialsProvider credentialsProvider() {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(r2Properties.accessKey(), r2Properties.secretKey()));
  }

  private S3Configuration s3Configuration() {
    return S3Configuration.builder()
        .chunkedEncodingEnabled(false)
        .pathStyleAccessEnabled(true)
        .build();
  }
}
