package com.sub9.productservice.product.application.validation;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.experimental.UtilityClass;
import org.apache.tika.Tika;

@UtilityClass
public class ImageValidator {
  private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("image/jpeg", "image/png");
  private static final long MAX_PIXELS = 9_000_000L; // 약 3000 x 3000 정도
  private static final Tika TIKA = new Tika();

  public String resolveMediaType(byte[] data) {
    validateNotEmpty(data);

    String mediaType = TIKA.detect(data);
    validateMediaType(mediaType);
    validatePixelSize(data);

    return mediaType;
  }

  private void validateNotEmpty(byte[] data) {
    if (data == null || data.length == 0) {
      throw new BusinessException(CommonErrorCode.BAD_REQUEST);
    }
  }

  private void validateMediaType(String mediaType) {
    if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {
      throw new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
  }

  private void validatePixelSize(byte[] data) {
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {

      var readers = ImageIO.getImageReaders(input);

      if (!readers.hasNext()) {
        throw new BusinessException(CommonErrorCode.BAD_REQUEST);
      }

      ImageReader reader = readers.next();

      try {
        reader.setInput(input, true, true);

        int width = reader.getWidth(0);
        int height = reader.getHeight(0);

        long pixels = (long) width * height;

        if (pixels > MAX_PIXELS) {
          throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
      } finally {
        reader.dispose();
      }

    } catch (IOException e) {
      throw new BusinessException(CommonErrorCode.BAD_REQUEST);
    }
  }
}
