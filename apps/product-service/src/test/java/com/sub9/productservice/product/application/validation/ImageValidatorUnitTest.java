package com.sub9.productservice.product.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.sub9.productservice.support.ImageTestFixture.imageBytes;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ImageValidator - 단위 테스트")
class ImageValidatorUnitTest {
  @ParameterizedTest
  @ValueSource(strings = {"jpeg", "png"})
  @DisplayName("실제 JPEG와 PNG를 검증하고 미디어 타입을 반환한다.")
  void resolveMediaType_success(String format) throws Exception {
    // given
    byte[] data = imageBytes(format);

    // when
    String result = ImageValidator.resolveMediaType(data);

    // then
    assertThat(result).isEqualTo("image/" + format);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("null 또는 빈 바이트는 BAD_REQUEST 예외가 발생해야한다.")
  void resolveMediaType_fails_when_data_is_empty(byte[] data) {
    // when & then
    assertThatThrownBy(() -> ImageValidator.resolveMediaType(data))
        .isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
  }

  @Test
  @DisplayName("이미지가 아니면 허용하지 않는다.")
  void resolveMediaType_fails_when_data_is_not_image() {
    // when & then
    assertThatThrownBy(() -> ImageValidator.resolveMediaType(new byte[] {1, 2, 3}))
        .isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE));
  }

  @Test
  @DisplayName("디코딩할 수 없는 파일은 BAD_REQUEST 예외가 발생해야한다.")
  void resolveMediaType_fails_when_image_is_truncated() throws Exception {
    // given
    byte[] data = Arrays.copyOf(imageBytes("png"), 16);

    // when & then
    assertThatThrownBy(() -> ImageValidator.resolveMediaType(data))
        .isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
  }
}
