package com.sub9.productservice.product.application.command.dto.sku;

public record CreateSkuCommand(String name, Long price, boolean isDefault, int quantity) {}
