package com.sub9.orderservice.payment.infrastructure.persistence;

import com.sub9.orderservice.payment.domain.model.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {

    @Override
    @EntityGraph(attributePaths = "cancellation")
    Optional<Payment> findById(UUID paymentId);

    @EntityGraph(attributePaths = "cancellation")
    Optional<Payment> findByOrderId(UUID orderId);
}
