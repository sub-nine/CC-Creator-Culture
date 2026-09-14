package com.sub9.productservice.product.application.port.in.image;

import com.sub9.productservice.product.application.command.dto.product.DeleteProductImageCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateImageSortOrderCommand;
import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;
import java.util.List;
import java.util.UUID;

public interface ProductImageCommandUseCase {
  void uploadImages(UUID productId, List<UploadImageCommand> images);

  void updateSortOrder(UpdateImageSortOrderCommand updateImageSortOrderCommand);

  void delete(DeleteProductImageCommand deleteProductImageCommand);
}
