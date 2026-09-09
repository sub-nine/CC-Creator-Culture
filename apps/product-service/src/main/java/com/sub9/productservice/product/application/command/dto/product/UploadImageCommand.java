package com.sub9.productservice.product.application.command.dto.product;

public record UploadImageCommand(String contentType, byte[] data) {}
