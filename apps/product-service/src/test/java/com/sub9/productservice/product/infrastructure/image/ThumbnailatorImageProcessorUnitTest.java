package com.sub9.productservice.product.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.port.ImageData;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ThumbnailatorImageProcessor - 단위 테스트")
class ThumbnailatorImageProcessorUnitTest {
  private final ThumbnailatorImageProcessor processor = new ThumbnailatorImageProcessor();

  @ParameterizedTest
  @ValueSource(strings = {"png", "jpeg"})
  @DisplayName("PNG와 JPEG 이미지를 최대 1200픽셀 JPEG로 변환한다.")
  void resize_success(String format) throws Exception {
    // given
    var original = new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB);
    var output = new ByteArrayOutputStream();

    ImageIO.write(original, format, output);

    // when
    ImageData result = processor.resize(new ImageData("image/" + format, output.toByteArray()));

    // then
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(result.data()).startsWith((byte) 0xff, (byte) 0xd8);
    BufferedImage resized = ImageIO.read(new ByteArrayInputStream(result.data()));
    assertThat(resized.getWidth()).isEqualTo(1200);
    assertThat(resized.getHeight()).isEqualTo(600);
  }

  @Test
  @DisplayName("빈 이미지는 UNSUPPORTED_MEDIA_TYPE 예외가 발생해야한다.")
  void resize_fails_when_image_is_empty() {
    // when & then
    assertThatThrownBy(() -> processor.resize(new ImageData("image/png", new byte[0])))
        .isInstanceOf(BusinessException.class)
        .hasMessage(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.message());
  }

  @Test
  @DisplayName("지원하지 않는 타입은 UNSUPPORTED_MEDIA_TYPE 예외가 발생해야한다.")
  void resize_fails_when_type_is_unsupported() {
    // when & then
    assertThatThrownBy(() -> processor.resize(new ImageData("image/gif", new byte[] {1})))
        .isInstanceOf(BusinessException.class)
        .hasMessage(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.message());
  }

  @Test
  @DisplayName("타입이 PNG여도 실제 내용이 이미지가 아니면 UNSUPPORTED_MEDIA_TYPE 예외가 발생해야한다.")
  void resize_fails_when_image_is_corrupted() {
    // when & then
    assertThatThrownBy(() -> processor.resize(new ImageData("image/png", new byte[] {1, 2, 3})))
        .isInstanceOf(BusinessException.class)
        .hasMessage(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.message());
  }
}
