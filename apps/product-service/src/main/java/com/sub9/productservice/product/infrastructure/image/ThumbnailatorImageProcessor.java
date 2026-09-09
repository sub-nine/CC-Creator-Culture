package com.sub9.productservice.product.infrastructure.image;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.port.ImageData;
import com.sub9.productservice.product.application.port.ImageProcessorPort;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ThumbnailatorImageProcessor implements ImageProcessorPort {
  private static final int MAX_DIMENSION = 1200;
  private static final double QUALITY = 0.8;

  @Override
  public ImageData resize(ImageData imageData) {
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData.data());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

      Thumbnails.of(inputStream)
          .size(MAX_DIMENSION, MAX_DIMENSION)
          .outputFormat("jpg")
          .outputQuality(QUALITY)
          .toOutputStream(outputStream);

      return new ImageData(MediaType.IMAGE_JPEG_VALUE, outputStream.toByteArray());
    } catch (IOException e) {
      throw new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
  }
}
