package com.sub9.productservice.product.application.port.in.image;

import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlCommand;
import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlResult;

public interface ImageUploadUseCase {
  CreatePresignedUrlResult createPresignedUrl(CreatePresignedUrlCommand command);
}
