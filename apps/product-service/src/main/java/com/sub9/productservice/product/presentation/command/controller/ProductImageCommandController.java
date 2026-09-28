package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.annotation.Creator;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.dto.product.DeleteProductImageCommand;
import com.sub9.productservice.product.application.port.in.image.ProductImageCommandUseCase;
import com.sub9.productservice.product.presentation.command.dto.product.UpdateImageSortOrderRequest;
import jakarta.validation.Valid;
import java.awt.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Creator
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/images")
public class ProductImageCommandController {
  private final ProductImageCommandUseCase imageCommandUseCase;

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
