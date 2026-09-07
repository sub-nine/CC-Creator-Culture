package com.sub9.orderservice.payment.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.application.port.output.PaymentCancellationPort;
import com.sub9.orderservice.payment.domain.exception.PaymentErrorCode;
import com.sub9.orderservice.payment.domain.model.Payment;
import com.sub9.orderservice.payment.domain.repository.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCancellationService implements PaymentCancellationPort {

    private final PaymentRepository paymentRepository;
    private final UuidV7Generator uuidGenerator;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(UUID orderId, UUID commandRequestId, Instant canceledAt) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.INVALID_PAYMENT_CANCELLATION));
        payment.cancel(uuidGenerator.generate(), commandRequestId, canceledAt);
        paymentRepository.save(payment);
    }
}
