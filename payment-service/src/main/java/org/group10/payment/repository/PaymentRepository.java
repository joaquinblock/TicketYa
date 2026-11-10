package org.group10.payment.repository;

import jakarta.persistence.LockModeType;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByActiveOrderId(Long activeOrderId);

    long countByOrderIdAndStatusAndIdLessThanEqual(Long orderId, PaymentStatus status, Long id);

    boolean existsByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findForUpdateByActiveOrderIdAndStatus(Long activeOrderId, PaymentStatus status);
}
