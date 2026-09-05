package com.sub9.orderservice.order.application.port.output;

import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StockRestoreCommand(
        UUID orderId,
        List<StockItem> items,
        RestoreReason reason
) {

    public StockRestoreCommand {
        Objects.requireNonNull(orderId, "주문 식별자는 필수입니다.");
        items = List.copyOf(Objects.requireNonNull(items, "재고 복구 상품은 필수입니다."));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("재고 복구 상품은 한 개 이상이어야 합니다.");
        }
        Objects.requireNonNull(reason, "재고 복구 사유는 필수입니다.");
    }
}
