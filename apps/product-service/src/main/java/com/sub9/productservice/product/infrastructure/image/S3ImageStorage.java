package com.sub9.productservice.product.infrastructure.image;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.common.config.s3.S3Properties;
import com.sub9.productservice.product.application.port.out.image.ImageData;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStoragePort {
  private final S3Properties s3Properties;
  private final S3Presigner s3Presigner;
  private final S3Client s3Client;

  @Override
  public String createPresignedPutUrl(String objectKey, String contentType, Duration expiration) {
    try {
      PutObjectRequest request =
          PutObjectRequest.builder()
              .bucket(s3Properties.bucket())
              .key(objectKey)
              .contentType(contentType)
              .build();

      PutObjectPresignRequest presignRequest =
          PutObjectPresignRequest.builder()
              .signatureDuration(expiration)
              .putObjectRequest(request)
              .build();

      return s3Presigner.presignPutObject(presignRequest).url().toString();
    } catch (SdkException e) {
      log.error("[ERROR] Presigned URL 생성 실패 ObjectKey = {}", objectKey, e);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public long getContentLength(String objectKey) {
    try {
      HeadObjectRequest request =
          HeadObjectRequest.builder().bucket(s3Properties.bucket()).key(objectKey).build();

      return s3Client.headObject(request).contentLength();
    } catch (NoSuchKeyException e) {
      throw new BusinessException(ProductErrorCode.IMAGE_UPLOAD_NOT_FOUND);
    } catch (SdkException e) {
      log.error("[ERROR] AWS S3 메타데이터 조회 실패 ObjectKey = {}", objectKey, e);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public String upload(String objectKey, ImageData ImageData) {
    try {
      PutObjectRequest request =
          PutObjectRequest.builder()
              .bucket(s3Properties.bucket())
              .key(objectKey)
              .contentType(ImageData.contentType())
              .build();

      s3Client.putObject(request, RequestBody.fromBytes(ImageData.data()));

      return objectKey;
    } catch (SdkException e) {
      log.error("[ERROR] AWS S3 저장 실패 ObjectKey = {}", objectKey);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public ImageData download(String objectKey) {
    try {
      GetObjectRequest request =
          GetObjectRequest.builder().bucket(s3Properties.bucket()).key(objectKey).build();

      ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);

      return new ImageData(response.response().contentType(), response.asByteArray());
    } catch (SdkException e) {
      log.error("[ERROR] AWS S3 다운로드 실패 ObjectKey = {}", objectKey, e);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      DeleteObjectRequest request =
          DeleteObjectRequest.builder().bucket(s3Properties.bucket()).key(objectKey).build();

      s3Client.deleteObject(request);
    } catch (SdkException e) {
      log.error("[ERROR] AWS S3 삭제 실패 ObjectKey = {}", objectKey, e);
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
