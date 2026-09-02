package com.sub9.productservice.product.application.command.dto;

public record CreateSkuCommand(String name, Long price, boolean isDefault, int quantity) {}
