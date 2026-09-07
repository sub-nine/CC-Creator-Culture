package com.sub9.orderservice.payment.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.application.port.input.PaymentResultUseCase;
import com.sub9.orderservice.order.application.port.output.StockRestoreCommand;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.repository.OrderRepository;
import com.sub9.orderservice.payment.domain.model.Payment;
import com.sub9.orderservice.payment.domain.model.PaymentStatus;
import com.sub9.orderservice.payment.domain.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MockPaymentTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentResultUseCase paymentResultUseCase;
    private final Clock clock;
    private final UuidV7Generator uuidGenerator;

    @Transactional
    public ProcessedPayment process(UUID customerId, OrderNumber orderNumber, PaymentStatus result) {
        Objects.requireNonNull(customerId, "인증된 사용자 ID가 필요합니다.");
        Objects.requireNonNull(orderNumber, "주문번호가 필요합니다.");
        Objects.requireNonNull(result, "결제 결과가 필요합니다.");

        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!order.getCustomerId().equals(customerId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        // 잠금 대기 시간을 반영하고 PostgreSQL 저장 후에도 같은 처리 시각을 반환합니다.
        Instant processedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        StockRestoreCommand stockRestore = null;
        if (result == PaymentStatus.SUCCESS) {
            paymentResultUseCase.markPaid(order.getId(), processedAt);
        } else {
            stockRestore = paymentResultUseCase.markPaymentFailed(order.getId(), processedAt);
        }

        Payment payment = paymentRepository.save(Payment.create(
                uuidGenerator.generate(), order.getId(), order.getPaymentAmount(), result, processedAt));
        return new ProcessedPayment(MockPaymentResult.from(payment, orderNumber), stockRestore);
    }

    public record ProcessedPayment(MockPaymentResult result, StockRestoreCommand stockRestore) {
    }
}
