package org.group10.ticket.repository;

import org.group10.ticket.entity.ReturnRequest;
import org.group10.ticket.entity.ReturnRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    @EntityGraph(attributePaths = {"ticket", "ticket.order"})
    Page<ReturnRequest> findByStatus(ReturnRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"ticket", "ticket.order"})
    Optional<ReturnRequest> findWithTicketById(Long id);

    @Modifying
    @Query("""
            update ReturnRequest r
            set r.status = :nextStatus, r.resolvedBy = :resolvedBy, r.resolvedAt = :resolvedAt
            where r.id = :id and r.status = org.group10.ticket.entity.ReturnRequestStatus.PENDING
            """)
    int resolvePendingRequest(Long id, ReturnRequestStatus nextStatus, Long resolvedBy, LocalDateTime resolvedAt);
}
