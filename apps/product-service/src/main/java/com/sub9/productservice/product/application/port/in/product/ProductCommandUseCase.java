package com.sub9.productservice.product.application.port.in.product;

import com.sub9.productservice.product.application.command.dto.product.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateProductCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateProductStatusCommand;
import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;
import java.util.List;
import java.util.UUID;

public interface ProductCommandUseCase {
  UUID createProduct(CreateProductCommand command, List<UploadImageCommand> images);

  void updateProduct(UpdateProductCommand command);

  void updateStatusProduct(UpdateProductStatusCommand command);

  void deleteProduct(UUID creatorId, UUID productId);
}
