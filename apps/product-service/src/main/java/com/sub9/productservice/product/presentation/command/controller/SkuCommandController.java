package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.annotation.Creator;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.dto.sku.DeleteSkuCommand;
import com.sub9.productservice.product.application.port.in.sku.SkuCommandUseCase;
import com.sub9.productservice.product.presentation.command.dto.sku.UpdateSkuRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Creator
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class SkuCommandController {
  private final SkuCommandUseCase skuCommandUseCase;

  @PatchMapping("/{productId}/skus/{skuId}")
  public ApiResponse<Void> updateSku(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @PathVariable UUID skuId,
      @Valid @RequestBody UpdateSkuRequest request) {
    skuCommandUseCase.updateSku(request.toCommand(authUser.id(), productId, skuId));
    return ApiResponse.success(null);
  }

  @DeleteMapping("/{productId}/skus/{skuId}")
  public ApiResponse<Void> deleteSku(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @PathVariable UUID skuId) {
    skuCommandUseCase.deleteSku(new DeleteSkuCommand(authUser.id(), productId, skuId));
    return ApiResponse.success(null);
  }
}
