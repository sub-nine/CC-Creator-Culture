package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.common.annotation.Creator;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.service.StockCommandService;
import com.sub9.productservice.product.presentation.command.dto.stock.AdjustStockRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Creator
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/skus")
public class StockCommandController {
  private final StockCommandService stockCommandService;

  @PostMapping("/{skuId}/stock/adjustments")
  public ApiResponse<Void> adjustStock(
      @AuthenticationPrincipal AuthUser authUser,
      @PathVariable UUID skuId,
      @Valid @RequestBody AdjustStockRequest request) {
    stockCommandService.adjust(request.toCommand(authUser.id(), skuId));
    return ApiResponse.success("상품 수량이 변경되었습니다.", null);
  }
}
