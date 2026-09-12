package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.annotation.Creator;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.port.in.product.ProductCommandUseCase;
import com.sub9.productservice.product.presentation.command.dto.product.CreateProductRequest;
import com.sub9.productservice.product.presentation.command.dto.product.CreateProductResponse;
import com.sub9.productservice.product.presentation.command.dto.product.UpdateProductRequest;
import com.sub9.productservice.product.presentation.command.dto.product.UpdateProductStatusRequest;
import com.sub9.productservice.product.presentation.command.mapper.UploadImageMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/products")
public class ProductCommandController {
  private final ProductCommandUseCase productCommandUseCase;

  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<CreateProductResponse> createProduct(
      @AuthenticationPrincipal AuthUser authUser,
      @Valid @RequestPart CreateProductRequest request,
      @RequestPart(value = "images", required = false)
          @Size(max = 5, message = "이미지는 최대 5개까지 등록할 수 있습니다.")
          List<MultipartFile> images) {

    UUID response =
        productCommandUseCase.createProduct(
            request.toCommand(authUser.id()), UploadImageMapper.from(images));

    return ApiResponse.success("상품 등록에 성공했습니다.", new CreateProductResponse(response));
  }

  @PatchMapping("/{productId}/status")
  public ApiResponse<Void> updateStatusProduct(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @Valid @RequestBody UpdateProductStatusRequest request) {
    productCommandUseCase.updateStatusProduct(
        request.toCommand(authUser.id(), productId, authUser.role()));
    return ApiResponse.success("상품 상태 변경에 성공했습니다.", null);
  }

  @PatchMapping("/{productId}")
  public ApiResponse<Void> updateProduct(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @Valid @RequestBody UpdateProductRequest request) {
    productCommandUseCase.updateProduct(request.toCommand(authUser.id(), productId));
    return ApiResponse.success("상품 수정에 성공했습니다.", null);
  }

  @DeleteMapping("/{productId}")
  public ApiResponse<Void> deleteProduct(
      @AuthenticationPrincipal AuthUser authUser, @PathVariable UUID productId) {
    productCommandUseCase.deleteProduct(authUser.id(), productId);
    return ApiResponse.success("상품 삭제에 성공했습니다.", null);
  }
}
