package com.sub9.productservice.product.infrastructure.image;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.common.config.r2.R2Properties;
import com.sub9.productservice.product.application.port.out.image.ImageData;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class R2ImageStorage implements ImageStoragePort {
  private final R2Properties r2Properties;
  private final S3Client s3Client;

  @Override
  public String upload(String objectKey, ImageData ImageData) {
    try {
      PutObjectRequest request =
          PutObjectRequest.builder()
              .bucket(r2Properties.bucket())
              .key(objectKey)
              .contentType(ImageData.contentType())
              .build();

      s3Client.putObject(request, RequestBody.fromBytes(ImageData.data()));

      return objectKey;
    } catch (SdkException e) {
      log.error("[ERROR] Cloudflare R2 저장 실패 = {}", objectKey);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public ImageData download(String objectKey) {
    try {
      GetObjectRequest request =
          GetObjectRequest.builder().bucket(r2Properties.bucket()).key(objectKey).build();

      ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);

      return new ImageData(response.response().contentType(), response.asByteArray());
    } catch (SdkException e) {
      log.error("[ERROR] Cloudflare R2 다운로드 실패 Key = {}", objectKey, e);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      DeleteObjectRequest request =
          DeleteObjectRequest.builder().bucket(r2Properties.bucket()).key(objectKey).build();

      s3Client.deleteObject(request);
    } catch (SdkException e) {
      log.error("[ERROR] Cloudflare R2 삭제 실패 Key = {}", objectKey, e);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
