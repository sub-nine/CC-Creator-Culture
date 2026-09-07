package com.sub9.productservice.product.presentation.command.controller;

import com.sub9.productservice.product.application.command.service.StockCommandService;
import com.sub9.productservice.product.presentation.command.dto.reqeust.stock.DeductStockRequest;
import com.sub9.productservice.product.presentation.command.dto.reqeust.stock.RestoreStockRequest;
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
  private final StockCommandService stockCommandService;

  @PostMapping("/deduct")
  public void deduct(@Valid @RequestBody DeductStockRequest request) {
    stockCommandService.deduct(request.toCommand());
  }

  @PostMapping("/restore")
  public void restore(@Valid @RequestBody RestoreStockRequest request) {
    stockCommandService.restore(request.toCommand());
  }
}
