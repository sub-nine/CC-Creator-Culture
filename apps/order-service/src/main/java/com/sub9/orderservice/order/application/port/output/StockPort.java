package com.sub9.orderservice.order.application.port.output;

import com.sub9.common.exception.BusinessException;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import java.util.List;
import java.util.UUID;

public interface StockPort {

    /**
     * 응답 유실처럼 차감 여부를 확인할 수 없으면 {@link StockOperationUncertainException}을 던집니다.
     */
    void deduct(UUID orderId, List<StockItem> items);

    /**
     * 응답 유실처럼 복구 여부를 확인할 수 없으면 {@link StockOperationUncertainException}을 던집니다.
     */
    void restore(UUID orderId, List<StockItem> items, RestoreReason reason);

    record StockItem(UUID skuId, int quantity) {

        public StockItem {
            if (skuId == null || quantity < 1) {
                throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
            }
        }
    }

    enum RestoreReason {
        ORDER_CREATION_FAILED
    }
}
