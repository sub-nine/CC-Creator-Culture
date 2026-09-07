package com.sub9.orderservice.order.application.port.input;

import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import java.time.Instant;
import java.util.UUID;

public interface PaymentResultUseCase {

    void markPaid(UUID orderId, Instant processedAt);

    /** 반환된 재고 복구 명령은 결제 로컬 트랜잭션이 커밋된 뒤 실행합니다. */
    StockRestoreCommand markPaymentFailed(UUID orderId, Instant processedAt);
}
