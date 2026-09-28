package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.annotation.Creator;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlResult;
import com.sub9.productservice.product.application.port.in.image.ImageUploadUseCase;
import com.sub9.productservice.product.presentation.command.dto.product.CreatePresignedUrlRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Creator
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/images")
public class ImageUploadController {
  private final ImageUploadUseCase uploadUseCase;

  @PostMapping("/presigned-url")
  @ResponseStatus(value = HttpStatus.CREATED)
  public ApiResponse<CreatePresignedUrlResult> getPreSignedUrl(
      @AuthenticationPrincipal AuthUser authUser,
      @Valid @RequestBody CreatePresignedUrlRequest request) {
    return ApiResponse.success(
        "이미지 업로드 URL 발급에 성공했습니다.",
        uploadUseCase.createPresignedUrl(request.toCommand(authUser.id())));
  }
}
