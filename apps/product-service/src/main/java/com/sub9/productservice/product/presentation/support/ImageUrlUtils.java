package com.sub9.productservice.product.presentation.support;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@UtilityClass
public class ImageUrlUtils {
  public String toImageUrl(String publicUrl, String imageKey) {
    if (!StringUtils.hasText(imageKey)) {
      return null;
    }

    return UriComponentsBuilder.fromUriString(publicUrl)
        .pathSegment(imageKey.split("/"))
        .build()
        .encode()
        .toUriString();
  }
}
