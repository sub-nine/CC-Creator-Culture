package com.sub9.productservice.product.application.port.in.image;

import com.sub9.productservice.product.application.command.dto.product.AddImagesCommand;
import com.sub9.productservice.product.application.command.dto.product.DeleteProductImageCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateImageSortOrderCommand;

public interface ProductImageCommandUseCase {
  void addImages(AddImagesCommand command);

  void updateSortOrder(UpdateImageSortOrderCommand command);

  void delete(DeleteProductImageCommand command);
}
