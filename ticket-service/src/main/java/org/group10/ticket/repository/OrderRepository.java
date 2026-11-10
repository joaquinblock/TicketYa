package org.group10.ticket.repository;

import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findByUserIdOrderByIdDesc(Long userId);

    @Modifying
    @Query("""
            update Order o set o.paymentAttemptKey = :attemptKey
            where o.id = :id
              and o.status = org.group10.ticket.entity.OrderStatus.PENDING
              and (o.paymentAttemptKey is null or o.paymentAttemptKey = :attemptKey)
            """)
    int takeOrderForPaymentAttempt(Long id, String attemptKey);

    @Modifying
    @Query("""
            update Order o set o.paymentAttemptKey = null
            where o.id = :id
              and o.status = org.group10.ticket.entity.OrderStatus.PENDING
              and o.paymentAttemptKey = :attemptKey
            """)
    int clearPaymentAttempt(Long id, String attemptKey);

    @Modifying
    @Query("""
            update Order o
            set o.status = org.group10.ticket.entity.OrderStatus.PAID,
                o.paymentMethod = :paymentMethod,
                o.paymentAttemptKey = null
            where o.id = :id and o.status = org.group10.ticket.entity.OrderStatus.PENDING
            """)
    int markPendingOrderAsPaid(Long id, PaymentMethod paymentMethod);

    @Query("""
            select o.id from Order o
            where o.status = org.group10.ticket.entity.OrderStatus.PENDING and o.expiresAt <= :moment
            """)
    List<Long> findIdsOfPendingOrdersOverdueAt(LocalDateTime moment);

    @Query("""
            select o.id from Order o
            where o.eventId = :eventId and o.status = org.group10.ticket.entity.OrderStatus.PENDING
            """)
    List<Long> findIdsOfPendingOrdersOfEvent(Long eventId);

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
