package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.common.security.annotation.Creator;
import com.sub9.productservice.product.application.command.service.ProductCommandService;
import com.sub9.productservice.product.presentation.command.dto.reqeust.CreateProductRequest;
import com.sub9.productservice.product.presentation.command.dto.reqeust.UpdateProductRequest;
import com.sub9.productservice.product.presentation.command.dto.reqeust.UpdateProductStatusRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Creator
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductCommandController {
  private final ProductCommandService productCommandService;

  @PostMapping
  public ResponseEntity<ApiResponse<Void>> createProduct(
      @AuthenticationPrincipal AuthUser authUser,
      @Valid @RequestBody CreateProductRequest request) {
    productCommandService.createProduct(request.toCommand(authUser.id()));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
  }

  @PatchMapping("/{productId}/status")
  public ApiResponse<Void> updateStatusProduct(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @Valid @RequestBody UpdateProductStatusRequest request) {
    productCommandService.updateStatusProduct(
        request.toCommand(authUser.id(), productId, authUser.role()));
    return ApiResponse.success(null);
  }

  @PatchMapping("/{productId}")
  public ApiResponse<Void> updateProduct(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @Valid @RequestBody UpdateProductRequest request) {
    productCommandService.updateProduct(request.toCommand(authUser.id(), productId));
    return ApiResponse.success(null);
  }

  @DeleteMapping("/{productId}")
  public ApiResponse<Void> deleteProduct(
      @AuthenticationPrincipal AuthUser authUser, @PathVariable UUID productId) {
    productCommandService.deleteProduct(authUser.id(), productId);
    return ApiResponse.success(null);
  }
}
