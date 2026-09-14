package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.productservice.product.application.port.in.stock.OrderStockUseCase;
import com.sub9.productservice.product.presentation.command.dto.stock.DeductStockRequest;
import com.sub9.productservice.product.presentation.command.dto.stock.RestoreStockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/stocks")
public class InternalStockCommandController {
  private final OrderStockUseCase orderStockUseCase;

  @PostMapping("/deduct")
  public void deduct(@Valid @RequestBody DeductStockRequest request) {
    orderStockUseCase.deduct(request.toCommand());
  }

  @PostMapping("/restore")
  public void restore(@Valid @RequestBody RestoreStockRequest request) {
    orderStockUseCase.restore(request.toCommand());
  }
}
