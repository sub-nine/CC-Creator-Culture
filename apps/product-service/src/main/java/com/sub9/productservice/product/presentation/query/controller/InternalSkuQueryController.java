package com.sub9.productservice.product.presentation.query.controller;

import com.sub9.productservice.product.application.port.in.product.CartProductQueryUseCase;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/skus")
public class InternalSkuQueryController {
  private final CartProductQueryUseCase cartProductQueryUseCase;

  @PostMapping
  public List<SkuInfo> getCartItemProducts(
      @RequestBody @NotNull @Size(max = 70) List<@NotNull UUID> skuIds) {
    return cartProductQueryUseCase.getCartItemProducts(skuIds);
  }

  @GetMapping("/{skuId}/validation")
  public void validateSkuForCart(@PathVariable UUID skuId) {
    cartProductQueryUseCase.validateSkuForCart(skuId);
  }
}
