package com.sub9.productservice.category.presentation.command.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank(message = "카테고리명은 필수입니다.")
        String name,
        String description
) {
}
