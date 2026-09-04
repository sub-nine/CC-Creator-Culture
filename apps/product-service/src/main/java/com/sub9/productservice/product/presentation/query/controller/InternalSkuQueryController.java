package com.sub9.productservice.product.presentation.query.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.product.application.query.service.ProductQueryService;
import com.sub9.productservice.product.presentation.query.dto.SkuResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/skus")
public class InternalSkuQueryController {
  private final ProductQueryService productQueryService;

  @PostMapping
  public ApiResponse<List<SkuResponse>> findAllSkuInfoByIds(
      @RequestBody @NotNull @Size(max = 70) List<@NotNull UUID> skuIds) {
    return ApiResponse.success(productQueryService.getSkus(skuIds));
  }
}
