package com.sub9.productservice.common.config.feign;

import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomErrorDecoder implements ErrorDecoder {

  @Override
  public Exception decode(String methodKey, Response response) {
    int status = response.status();
    if (status >= 500) {
      log.error(
          "[Feign Error] method = {}, status = {}, url = {}",
          methodKey,
          response.status(),
          response.request().url());
    } else {
      log.warn(
          "[Feign Error] method = {}, status = {}, url = {}",
          methodKey,
          status,
          response.request().url());
    }

    return FeignException.errorStatus(methodKey, response);
  }
}
