package org.group10.payment.repository;

import jakarta.persistence.LockModeType;
import org.group10.payment.entity.Refund;
import org.group10.payment.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByTicketId(Long ticketId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Refund> findForUpdateById(Long id);

    @Query("""
            select coalesce(sum(refund.amount), 0)
            from Refund refund
            where refund.payment.id = :paymentId and refund.status <> :excludedStatus
            """)
    BigDecimal sumAmountOfThePaymentExcludingStatus(@Param("paymentId") Long paymentId,
                                                    @Param("excludedStatus") RefundStatus excludedStatus);
}
