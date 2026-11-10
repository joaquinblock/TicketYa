package org.group10.payment.repository;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    long countByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
