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
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

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

    @Modifying
    @Query("""
            update ReturnRequest r set r.status = org.group10.ticket.entity.ReturnRequestStatus.CANCELLED
            where r.status = org.group10.ticket.entity.ReturnRequestStatus.PENDING
              and r.ticket.id in (select t.id from Ticket t where t.order.eventId = :eventId)
            """)
    int cancelPendingRequestsOfEvent(Long eventId);

    @Query("""
            select r.ticket.id from ReturnRequest r
            where r.ticket.id in :ticketIds and r.status = org.group10.ticket.entity.ReturnRequestStatus.PENDING
            """)
    Set<Long> findTicketIdsWithPendingRequest(Collection<Long> ticketIds);

    @Query("""
            select r.ticket.id from ReturnRequest r
            where r.ticket.id in :ticketIds and r.status = org.group10.ticket.entity.ReturnRequestStatus.REJECTED
            """)
    Set<Long> findTicketIdsWithRejectedRequest(Collection<Long> ticketIds);
}
