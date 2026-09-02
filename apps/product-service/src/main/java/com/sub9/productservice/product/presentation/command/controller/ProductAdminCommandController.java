package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.common.security.annotation.Manager;
import com.sub9.productservice.product.application.command.service.ProductCommandService;
import com.sub9.productservice.product.presentation.command.dto.reqeust.UpdateAdminProductStatusRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/products")
public class ProductAdminCommandController {
  private final ProductCommandService productCommandService;

  @Manager
  @PatchMapping("/{productId}/status")
  public ApiResponse<Void> updateProductStatus(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @Valid @RequestBody UpdateAdminProductStatusRequest request) {
    productCommandService.updateStatusProduct(
        request.toCommand(authUser.id(), productId, authUser.role()));
    return ApiResponse.success(null);
  }
}
