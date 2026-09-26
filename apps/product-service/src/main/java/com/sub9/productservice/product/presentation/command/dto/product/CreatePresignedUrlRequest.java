package com.sub9.productservice.product.presentation.command.dto.product;

import com.sub9.productservice.product.application.command.dto.product.CreatePresignedUrlCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CreatePresignedUrlRequest(@NotBlank String contentType, @Positive long fileSize) {
  public CreatePresignedUrlCommand toCommand(UUID creatorId) {
    return new CreatePresignedUrlCommand(creatorId, contentType, fileSize);
  }
}
