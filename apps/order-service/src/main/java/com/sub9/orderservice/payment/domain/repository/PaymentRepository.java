package com.sub9.orderservice.payment.domain.repository;

import com.sub9.orderservice.payment.domain.model.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findById(UUID paymentId);
}
