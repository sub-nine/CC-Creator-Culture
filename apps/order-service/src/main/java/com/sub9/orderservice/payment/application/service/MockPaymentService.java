package com.sub9.orderservice.payment.application.service;

import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.payment.application.dto.MockPaymentResult;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockPaymentService {

    private final MockPaymentTransactionService transactionService;
    private final StockPort stockPort;

    // 재고 복구 전에 결제 커밋이 끝나야 하므로 외부 트랜잭션 참여를 금지합니다.
    @Transactional(propagation = Propagation.NEVER)
    public MockPaymentResult process(UUID customerId, OrderNumber orderNumber, PaymentStatus result) {
        var processed = transactionService.process(customerId, orderNumber, result);
        StockRestoreCommand command = processed.stockRestore();
        if (command != null) {
            try {
                stockPort.restore(command.orderId(), command.items(), command.reason());
            } catch (RuntimeException exception) {
                // ponytail: 복구 실패는 로그만 기록하며 전달 보장이 필요하면 영속 재시도 작업을 추가합니다.
                log.error("결제 실패 후 재고 복구에 실패했습니다. orderId={}, reason={}",
                        command.orderId(), command.reason(), exception);
            }
        }
        return processed.result();
    }
}
