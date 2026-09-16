package com.sub9.orderservice.order.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductPurchaseInfo(UUID productId, boolean purchased) {
}
