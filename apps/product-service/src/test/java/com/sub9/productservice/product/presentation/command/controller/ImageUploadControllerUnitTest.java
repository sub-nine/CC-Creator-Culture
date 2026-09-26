package com.sub9.productservice.product.presentation.command.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlCommand;
import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlResult;
import com.sub9.productservice.product.application.port.in.image.ImageUploadUseCase;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ImageUploadController.class)
@DisplayName("ImageUploadController - 단위 테스트")
class ImageUploadControllerUnitTest extends AbstractControllerTest {
  @MockitoBean ImageUploadUseCase imageUploadUseCase;

  private final UUID creatorId = UUID.randomUUID();
  private final String endPoint = "/api/v1/images/presigned-url";

  @Test
  @DisplayName("유효한 이미지 정보로 presigned URL을 발급한다.")
  void getPreSignedUrl_success() throws Exception {
    UUID uploadId = UUID.randomUUID();
    given(imageUploadUseCase.createPresignedUrl(any()))
        .willReturn(new CreatePresignedUrlResult(uploadId, "https://upload.example.com/image"));
    var request = java.util.Map.of("contentType", "image/png", "fileSize", 1024);

    mockMvc
        .perform(
            post(endPoint)
                .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR")))
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.uploadId").value(uploadId.toString()))
        .andExpect(jsonPath("$.data.uploadUrl").value("https://upload.example.com/image"));
    verify(imageUploadUseCase)
        .createPresignedUrl(new CreatePresignedUrlCommand(creatorId, "image/png", 1024));
  }

  @Test
  @DisplayName("허용되지 않은 이미지 타입이면 presigned URL 발급을 실패한다.")
  void getPreSignedUrl_fails_when_content_type_is_invalid() throws Exception {
    willThrow(new BusinessException(ProductErrorCode.IMAGE_UPLOAD_NOT_FOUND))
        .given(imageUploadUseCase)
        .createPresignedUrl(any());
    var request = java.util.Map.of("contentType", "image/gif", "fileSize", 1024);

    mockMvc
        .perform(
            post(endPoint)
                .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR")))
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value(ProductErrorCode.IMAGE_UPLOAD_NOT_FOUND.code()));
  }
}
