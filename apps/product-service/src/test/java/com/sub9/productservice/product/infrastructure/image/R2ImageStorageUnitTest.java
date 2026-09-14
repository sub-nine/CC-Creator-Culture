package com.sub9.productservice.product.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.common.config.r2.R2Properties;
import com.sub9.productservice.product.application.port.out.image.ImageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("R2ImageStorage - 단위 테스트")
class R2ImageStorageUnitTest {
  @Mock S3Client s3Client;
  private R2ImageStorage storage;
  private final String objectKey = "products/product/images/original/image";
  private final byte[] bytes = {1, 2, 3};

  @BeforeEach
  void setUp() {
    storage =
        new R2ImageStorage(
            new R2Properties(
                "test",
                "test",
                "https://example.invalid",
                "test-bucket",
                "https://images.example.com"),
            s3Client);
  }

  @Nested
  @DisplayName("원본 및 처리 이미지 업로드 테스트")
  class UploadTests {
    @Test
    @DisplayName("지정한 키와 타입으로 이미지 바이트를 업로드하고 키를 반환한다.")
    void upload_success() throws Exception {
      // when
      String result = storage.upload(objectKey, new ImageData("image/png", bytes));

      // then
      var request = ArgumentCaptor.forClass(PutObjectRequest.class);
      var body = ArgumentCaptor.forClass(RequestBody.class);

      verify(s3Client).putObject(request.capture(), body.capture());

      assertThat(result).isEqualTo(objectKey);
      assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
      assertThat(request.getValue().key()).isEqualTo(objectKey);
      assertThat(request.getValue().contentType()).isEqualTo("image/png");

      try (var stream = body.getValue().contentStreamProvider().newStream()) {
        assertThat(stream.readAllBytes()).containsExactly(bytes);
      }
    }

    @Test
    @DisplayName("R2 업로드에 실패하면 INTERNAL_SERVER_ERROR 예외가 발생해야한다..")
    void upload_fails_when_storage_unavailable() {
      // given
      given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
          .willThrow(SdkClientException.create("R2 unavailable"));

      // when & then
      assertThatThrownBy(() -> storage.upload(objectKey, new ImageData("image/png", bytes)))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CommonErrorCode.INTERNAL_SERVER_ERROR.message());
    }
  }

  @Nested
  @DisplayName("이미지 다운로드 테스트")
  class DownloadTests {
    @Test
    @DisplayName("저장된 이미지 타입과 바이트를 반환한다.")
    void download_success() {
      // given
      given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
          .willReturn(
              ResponseBytes.fromByteArray(
                  GetObjectResponse.builder().contentType("image/png").build(), bytes));

      // when
      ImageData image = storage.download(objectKey);

      // then
      assertThat(image.contentType()).isEqualTo("image/png");
      assertThat(image.data()).containsExactly(bytes);

      var request = ArgumentCaptor.forClass(GetObjectRequest.class);

      verify(s3Client).getObjectAsBytes(request.capture());

      assertThat(request.getValue().key()).isEqualTo(objectKey);
      assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
    }

    @Test
    @DisplayName("다운로드에 실패하면 INTERNAL_SERVER_ERROR 예외가 발생해야한다.")
    void download_fails_when_storage_unavailable() {
      // given
      given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
          .willThrow(SdkClientException.create("R2 unavailable"));

      // when & then
      assertThatThrownBy(() -> storage.download(objectKey))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CommonErrorCode.INTERNAL_SERVER_ERROR.message());
    }
  }
}
