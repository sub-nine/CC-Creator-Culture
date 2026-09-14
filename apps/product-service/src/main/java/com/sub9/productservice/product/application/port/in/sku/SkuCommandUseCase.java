package com.sub9.productservice.product.application.port.in.sku;

import com.sub9.productservice.product.application.command.dto.sku.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.dto.sku.UpdateSkuCommand;

public interface SkuCommandUseCase {
  void updateSku(UpdateSkuCommand command);

  void deleteSku(DeleteSkuCommand command);
}
