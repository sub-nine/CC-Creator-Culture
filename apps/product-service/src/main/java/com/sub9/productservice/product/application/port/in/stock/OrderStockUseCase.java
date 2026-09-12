package com.sub9.productservice.product.application.port.in.stock;

import com.sub9.productservice.product.application.command.dto.stock.DeductStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;

public interface OrderStockUseCase {
  void deduct(DeductStockCommand command);

  void restore(RestoreStockCommand command);
}
