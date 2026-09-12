package com.sub9.productservice.product.presentation.query.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.config.r2.R2Properties;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.port.in.product.ProductQueryUseCase;
import com.sub9.productservice.product.presentation.query.dto.ProductDetailResponse;
import com.sub9.productservice.product.presentation.query.dto.ProductResponse;
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
  private final ProductQueryUseCase productQueryUseCase;
  private final R2Properties r2Properties;

  @GetMapping
  public ApiResponse<Page<ProductResponse>> searchProducts(
      @RequestParam(required = false) String keyword,
      // TODO : 추후 검증 조건 및 페이징 조건 추가(현재 sort 값 사용 안함)
      //        파라미터 체크도 해야함
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<ProductResponse> response =
        productQueryUseCase
            .searchProducts(keyword, pageable)
            .map(info -> ProductResponse.of(info, r2Properties.publicUrl()));
    return ApiResponse.success(response);
  }

  @GetMapping("/{productId}")
  public ApiResponse<ProductDetailResponse> getProductDetail(
      @AuthenticationPrincipal AuthUser authUser, @PathVariable UUID productId) {
    UUID visitorId = authUser != null ? authUser.id() : null;
    var response = productQueryUseCase.getProductDetail(productId, visitorId);

    return ApiResponse.success(ProductDetailResponse.of(response, r2Properties.publicUrl()));
  }
}
