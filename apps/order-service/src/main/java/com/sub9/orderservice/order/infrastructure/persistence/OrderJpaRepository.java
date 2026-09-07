package com.sub9.orderservice.order.infrastructure.persistence;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderNumber;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import java.time.Instant;
import java.util.List;
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
    // 상품은 주문 잠금을 얻은 뒤 조회해야 대기 중 커밋된 배송 상태를 읽을 수 있습니다.
    @Query("select o from Order o where o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") OrderNumber orderNumber);

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

    @Query("""
            select o.id
              from Order o
             where o.status = :status
               and o.expiresAt <= :now
             order by o.expiresAt, o.id
            """)
    List<UUID> findExpiredOrderIds(
            @Param("status") OrderStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(OrderNumber orderNumber);

    Page<Order> findAllByCustomerId(UUID customerId, Pageable pageable);
}
