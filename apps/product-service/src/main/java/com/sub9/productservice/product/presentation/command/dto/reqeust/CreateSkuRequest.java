package com.sub9.productservice.product.presentation.command.dto.reqeust;

import com.sub9.productservice.product.application.command.dto.CreateSkuCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateSkuRequest(
        @NotBlank(message = "옵션명은 필수입니다.")
        @Size(max = 25, message = "옵션명은 25자를 초과할 수 없습니다.")
        String name,

        @NotNull(message = "가격은 필수입니다.")
        @PositiveOrZero(message = "가격은 0원 이상이어야 합니다.")
        Long price,

        boolean isDefault,

        @PositiveOrZero(message = "재고는 0개 이상이어야 합니다.")
        int quantity
) {
    public CreateSkuCommand toCommand() {
        return new CreateSkuCommand(
                name,
                price,
                isDefault,
                quantity
        );
    }
}

