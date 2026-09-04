package com.sub9.orderservice.order.presentation.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotEmpty(message = "주문 상품은 한 개 이상이어야 합니다.")
        List<@NotNull(message = "주문 상품은 필수입니다.") @Valid Item> items,

        @NotNull(message = "배송지는 필수입니다.")
        @Valid ShippingAddress shippingAddress
) {

    public record Item(
            @NotNull(message = "장바구니 항목 식별자는 필수입니다.") UUID cartItemId,
            UUID userCouponId
    ) {
    }

    public record ShippingAddress(
            @NotBlank(message = "수령인 이름은 필수입니다.")
            @Size(max = 50, message = "수령인 이름은 50자 이하여야 합니다.")
            String recipientName,

            @NotBlank(message = "수령인 연락처는 필수입니다.")
            @Size(max = 20, message = "수령인 연락처는 20자 이하여야 합니다.")
            String recipientPhone,

            @NotBlank(message = "우편번호는 필수입니다.")
            @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
            String postalCode,

            @NotBlank(message = "기본 주소는 필수입니다.")
            @Size(max = 200, message = "기본 주소는 200자 이하여야 합니다.")
            String addressLine1,

            @Size(max = 200, message = "상세 주소는 200자 이하여야 합니다.")
            String addressLine2
    ) {
    }
}
