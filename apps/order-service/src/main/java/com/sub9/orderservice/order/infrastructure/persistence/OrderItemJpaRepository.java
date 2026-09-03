package com.sub9.orderservice.order.infrastructure.persistence;

import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemJpaRepository extends JpaRepository<OrderItem, UUID> {

    @Query(
            value = """
                    select item
                      from OrderItem item
                      join fetch item.order parent
                     where item.creatorId = :creatorId
                       and parent.status in :statuses
                    """,
            countQuery = """
                    select count(item)
                      from OrderItem item
                      join item.order parent
                     where item.creatorId = :creatorId
                       and parent.status in :statuses
                    """)
    Page<OrderItem> findAllVisibleByCreatorId(
            @Param("creatorId") UUID creatorId,
            @Param("statuses") Set<OrderStatus> statuses,
            Pageable pageable);

    @Query("""
            select item
              from OrderItem item
              join fetch item.order parent
             where item.id = :orderItemId
            """)
    Optional<OrderItem> findDetailById(@Param("orderItemId") UUID orderItemId);
}
