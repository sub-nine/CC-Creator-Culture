package com.sub9.productservice.product.application.validation;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ImageValidator {
  public String resolveMediaType(byte[] data) {
    validateNotEmpty(data);
    return validateImageFormat(data);
  }

  private void validateNotEmpty(byte[] data) {
    if (data == null || data.length == 0) {
      throw new BusinessException(CommonErrorCode.BAD_REQUEST);
    }
  }

  private String validateImageFormat(byte[] data) {
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
      }

      ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName();
        if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format)) {
          throw new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        reader.setInput(input);
        if (reader.read(0) == null) {
          throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        return "JPEG".equalsIgnoreCase(format) ? "image/jpeg" : "image/png";
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new BusinessException(CommonErrorCode.BAD_REQUEST);
    }
  }
}
