package com.sub9.productservice.product.application.port.in.stock;

import com.sub9.productservice.product.application.command.dto.stock.AdjustStockCommand;

public interface AdjustStockUseCase {
  void adjust(AdjustStockCommand request);
}
