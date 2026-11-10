package org.group10.ticket.repository;

import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select o.id from Order o
            where o.status = org.group10.ticket.entity.OrderStatus.PENDING and o.expiresAt <= :moment
            """)
    List<Long> findIdsOfPendingOrdersOverdueAt(LocalDateTime moment);

    @Modifying
    @Query("""
            update Order o set o.status = :nextStatus
            where o.id = :id and o.status = org.group10.ticket.entity.OrderStatus.PENDING
            """)
    int closePendingOrder(Long id, OrderStatus nextStatus);

    @Modifying
    @Query("""
            update Order o set o.status = org.group10.ticket.entity.OrderStatus.CANCELLED
            where o.id = :id
              and o.status = org.group10.ticket.entity.OrderStatus.PENDING
              and o.paymentAttemptKey is null
            """)
    int cancelPendingOrderWithoutPaymentInProgress(Long id);
}
