package com.sub9.productservice.product.presentation.query.controller;

import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.product.application.query.service.ProductQueryService;
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
  private final ProductQueryService productQueryService;

  @PostMapping
  public List<SkuInfo> getCartItemProducts(
      @RequestBody @NotNull @Size(max = 70) List<@NotNull UUID> skuIds) {
    return productQueryService.getCartItemProducts(skuIds);
  }

  @GetMapping("/{skuId}/validation")
  public void validateSkuForCart(@PathVariable UUID skuId) {
    productQueryService.validateSkuForCart(skuId);
  }
}
