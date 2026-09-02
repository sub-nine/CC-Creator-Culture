package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.dto.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.service.SkuCommandService;
import com.sub9.productservice.product.presentation.command.dto.reqeust.UpdateSkuRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class SkuCommandController {
  private final SkuCommandService skuCommandService;

  @PatchMapping("/{productId}/skus/{skuId}")
  public ApiResponse<Void> updateSku(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @PathVariable UUID skuId,
      @Valid @RequestBody UpdateSkuRequest request) {
    skuCommandService.updateSku(request.toCommand(authUser.id(), productId, skuId));
    return ApiResponse.success(null);
  }

  @DeleteMapping("/{productId}/skus/{skuId}")
  public ApiResponse<Void> deleteSku(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @PathVariable UUID skuId) {
    skuCommandService.deleteSku(new DeleteSkuCommand(authUser.id(), productId, skuId));
    return ApiResponse.success(null);
  }
}
