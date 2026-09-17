package com.sub9.productservice.product.presentation.query.controller;

import static com.sub9.productservice.product.presentation.support.VisitorCookieResolver.VISITOR_COOKIE;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.config.r2.R2Properties;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.port.in.product.ProductQueryUseCase;
import com.sub9.productservice.product.presentation.query.dto.ProductDetailResponse;
import com.sub9.productservice.product.presentation.query.dto.ProductResponse;
import com.sub9.productservice.product.presentation.support.VisitorCookieResolver;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<ProductResponse> response =
        productQueryUseCase
            .searchProducts(keyword, normalizePageSize(pageable))
            .map(info -> ProductResponse.of(info, r2Properties.publicUrl()));
    return ApiResponse.success(response);
  }

  @GetMapping("/{productId}")
  public ApiResponse<ProductDetailResponse> getProductDetail(
      @AuthenticationPrincipal AuthUser authUser,
      @CookieValue(value = VISITOR_COOKIE, required = false) String visitorCookie,
      @PathVariable UUID productId,
      HttpServletResponse response) {

    String visitorId = VisitorCookieResolver.resolve(authUser, visitorCookie, response);
    var result = productQueryUseCase.getProductDetail(productId, visitorId);

    return ApiResponse.success(ProductDetailResponse.of(result, r2Properties.publicUrl()));
  }

  private Pageable normalizePageSize(Pageable pageable) {
    int requestedSize = pageable.getPageSize();
    int size = requestedSize == 30 || requestedSize == 50 ? requestedSize : 10;

    return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
  }
}
