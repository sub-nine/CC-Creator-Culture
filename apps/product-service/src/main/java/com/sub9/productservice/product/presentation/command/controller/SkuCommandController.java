package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.annotation.Creator;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.dto.sku.DeleteSkuCommand;
import com.sub9.productservice.product.application.port.in.sku.SkuCommandUseCase;
import com.sub9.productservice.product.presentation.command.dto.sku.CreateSkuRequest;
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

  @PostMapping("/{productId}/skus")
  public ApiResponse<UUID> addSku(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @Valid @RequestBody CreateSkuRequest request) {
    UUID skuId = skuCommandUseCase.addSku(request.toCommand(productId, authUser.id()));
    return ApiResponse.success("상품 옵션 추가에 성공했습니다.", skuId);
  }

  @PatchMapping("/{productId}/skus/{skuId}")
  public ApiResponse<Void> updateSku(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @PathVariable UUID skuId,
      @Valid @RequestBody UpdateSkuRequest request) {
    skuCommandUseCase.updateSku(request.toCommand(authUser.id(), productId, skuId));
    return ApiResponse.success("상품 옵션 수정에 성공했습니다.", null);
  }

  @DeleteMapping("/{productId}/skus/{skuId}")
  public ApiResponse<Void> deleteSku(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID productId,
      @PathVariable UUID skuId) {
    skuCommandUseCase.deleteSku(new DeleteSkuCommand(authUser.id(), productId, skuId));
    return ApiResponse.success("상품 옵션 삭제에 성공했습니다.", null);
  }
}
