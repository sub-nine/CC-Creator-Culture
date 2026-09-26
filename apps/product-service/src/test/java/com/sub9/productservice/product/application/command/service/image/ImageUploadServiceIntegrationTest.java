package com.sub9.productservice.product.application.command.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlCommand;
import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import com.sub9.productservice.product.domain.model.ImageUpload;
import com.sub9.productservice.product.infrastructure.persistence.command.product.ImageUploadJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@DisplayName("ImageUploadService - 통합 테스트")
class ImageUploadServiceIntegrationTest extends AbstractIntegrationTest {
  @Autowired ImageUploadService imageUploadService;
  @Autowired ImageUploadJpaRepository imageUploadRepository;
  @Autowired EntityManager entityManager;
  @MockitoBean ImageStoragePort imageStoragePort;

  private final UUID creatorId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(CustomAuthenticationToken.of(creatorId, "CREATOR"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("presigned URL을 발급하고 임시 업로드 정보를 저장한다.")
  void createPresignedUrl_success() {
    given(
            imageStoragePort.createPresignedPutUrl(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("image/png"),
                org.mockito.ArgumentMatchers.any()))
        .willReturn("https://upload.example.com/image");

    var result =
        imageUploadService.createPresignedUrl(
            new CreatePresignedUrlCommand(creatorId, "image/png", 1024L));
    entityManager.flush();
    entityManager.clear();

    ImageUpload saved = imageUploadRepository.findById(result.uploadId()).orElseThrow();
    assertThat(result.uploadUrl()).isEqualTo("https://upload.example.com/image");
    assertThat(saved.getObjectKey()).startsWith("products/images/original/");
    assertThat(saved.getContentType()).isEqualTo("image/png");
    assertThat(saved.getCreatedBy()).isEqualTo(creatorId);
  }
}
