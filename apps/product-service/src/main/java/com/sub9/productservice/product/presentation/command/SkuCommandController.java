package com.sub9.productservice.product.presentation.command;

import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.dto.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.service.SkuCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class SkuCommandController {
  private final SkuCommandService skuCommandService;

  @DeleteMapping("/{productId}/skus/{skuId}")
  public void deleteSku(
      @AuthenticationPrincipal AuthUser creatorUser,
      @PathVariable UUID productId,
      @PathVariable UUID skuId) {
    skuCommandService.deleteSku(new DeleteSkuCommand(creatorUser.id(), productId, skuId));
  }
}
