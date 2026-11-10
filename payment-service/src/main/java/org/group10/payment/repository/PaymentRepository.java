package org.group10.payment.repository;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByActiveOrderId(Long activeOrderId);

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    long countByOrderIdAndStatusAndIdLessThanEqual(Long orderId, PaymentStatus status, Long id);
}
