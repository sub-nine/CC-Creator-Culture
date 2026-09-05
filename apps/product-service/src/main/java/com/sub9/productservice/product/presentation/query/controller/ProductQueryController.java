package com.sub9.productservice.product.presentation.query.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.query.service.ProductQueryService;
import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductQueryController {
  private final ProductQueryService productQueryService;

  @GetMapping
  public ApiResponse<Page<ProductInfo>> searchProducts(
      @RequestParam(required = false) String keyword,
      // TODO : 추후 검증 조건 및 페이징 조건 추가(현재 sort 값 사용 안함)
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ApiResponse.success(productQueryService.searchProducts(keyword, pageable));
  }

  @GetMapping("/{productId}")
  public ApiResponse<ProductDetailInfo> getProductDetail(
          @AuthenticationPrincipal AuthUser authUser,
          @PathVariable UUID productId) {
    UUID visitorId = authUser != null ? authUser.id() : null;
    return ApiResponse.success(productQueryService.getProductDetail(productId, visitorId));
  }
}
