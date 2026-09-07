package com.sub9.orderservice.payment.presentation.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MockPaymentRequest(
        @NotBlank(message = "결제 결과는 필수입니다.")
        @Pattern(regexp = "SUCCESS|FAILED", message = "결제 결과는 SUCCESS 또는 FAILED여야 합니다.")
        String result
) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("결제 요청에는 result만 입력할 수 있습니다.");
    }
}
