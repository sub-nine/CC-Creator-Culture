package com.sub9.orderservice.order.infrastructure.persistence;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<Order, UUID> {

    @Lock(PESSIMISTIC_WRITE)
    @Query("select o from Order o join fetch o.items where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(PESSIMISTIC_WRITE)
    @Query("""
            select parent
              from Order parent
              join fetch parent.items
             where parent.id = (
                    select target.order.id
                      from OrderItem target
                     where target.id = :orderItemId
             )
            """)
    Optional<Order> findByOrderItemIdForUpdate(@Param("orderItemId") UUID orderItemId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(OrderNumber orderNumber);

    Page<Order> findAllByCustomerId(UUID customerId, Pageable pageable);
}
