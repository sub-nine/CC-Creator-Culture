package com.sub9.orderservice.config;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFeignConfig {
  // TODO : 추후 서비스 간 내부 인증 사용시 적용
  //  private final String internalToken;

  //  public OpenFeignConfig(@Value("${}") String internalToken) {
  //    this.internalToken = internalToken;
  //  }

  //  @Bean
  //  public RequestInterceptor requestInterceptor() {
  //    return requestTemplate -> {
  //      requestTemplate.header("X-Internal-Token", internalToken);
  //    };
  //  }

  @Bean
  public ErrorDecoder errorDecoder() {
    return new CustomErrorDecoder();
  }
}
