package com.sub9.orderservice.config;

import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomErrorDecoder implements ErrorDecoder {

  @Override
  public Exception decode(String methodKey, Response response) {
    log.error(
        "[Feign Error] method = {}, status = {}, url = {}",
        methodKey,
        response.status(),
        response.request().url());

    return FeignException.errorStatus(methodKey, response);
  }
}
