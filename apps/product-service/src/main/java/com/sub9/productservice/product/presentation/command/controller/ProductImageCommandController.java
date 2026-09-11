package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.annotation.Creator;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.dto.product.DeleteProductImageCommand;
import com.sub9.productservice.product.application.port.in.image.ProductImageCommandUseCase;
import com.sub9.productservice.product.presentation.command.dto.product.UpdateImageSortOrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.awt.*;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Creator
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/images")
public class ProductImageCommandController {
  private final ProductImageCommandUseCase imageCommandUseCase;

  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Void> addImages(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @Valid
          @RequestPart(value = "images", required = false)
          @Size(max = 5, message = "이미지는 최대 5개까지 등록할 수 있습니다.")
          List<MultipartFile> images) {
    // TODO : [PRODUCT] 추후 개발사항
    throw new UnsupportedOperationException("개발 중 입니다.");
    // return ApiResponse.success("이미지 등록에 성공했습니다.", null);
  }

  @PatchMapping
  public ApiResponse<Void> updateImage(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @Valid @RequestBody UpdateImageSortOrderRequest request) {
    imageCommandUseCase.updateSortOrder(request.toCommand(productId, authUser.id()));
    return ApiResponse.success("이미지 순서 변경에 성공했습니다.", null);
  }

  @DeleteMapping("/{imageId}")
  public ApiResponse<Void> deleteImage(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @PathVariable UUID imageId) {
    imageCommandUseCase.delete(new DeleteProductImageCommand(authUser.id(), productId, imageId));
    return ApiResponse.success("이미지 삭제에 성공했습니다.", null);
  }
}
