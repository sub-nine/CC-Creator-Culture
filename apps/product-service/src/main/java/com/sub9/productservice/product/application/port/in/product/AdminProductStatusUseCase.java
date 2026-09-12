package com.sub9.productservice.product.application.port.in.product;

import com.sub9.productservice.product.application.command.dto.product.UpdateProductStatusCommand;

public interface AdminProductStatusUseCase {
  void updateStatusProduct(UpdateProductStatusCommand command);
}
