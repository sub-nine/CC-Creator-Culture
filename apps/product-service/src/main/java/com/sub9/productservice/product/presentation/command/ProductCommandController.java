package com.sub9.productservice.product.presentation.command;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.product.application.command.service.ProductCommandService;
import com.sub9.productservice.product.presentation.command.dto.reqeust.CreateProductRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductCommandController {
  private final ProductCommandService productCommandService;

  @PostMapping
  public ResponseEntity<ApiResponse<Void>> createProduct(
      @AuthenticationPrincipal AuthUser authUser,
      @Valid @RequestBody CreateProductRequest request) {
    productCommandService.createProduct(request.toCommand(authUser.id()));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
  }
}
