package org.group10.ticket.repository;

import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByOrderIdOrderById(Long orderId);

    List<Ticket> findByOrderIdInOrderById(Collection<Long> orderIds);

    @Query("""
            select count(t) from Ticket t
            where t.order.eventId = :eventId and t.status = org.group10.ticket.entity.TicketStatus.RESERVED
            """)
    long countReservedTicketsOfEvent(Long eventId);

    @Query("""
            select count(t) from Ticket t
            where t.order.userId = :userId and t.order.eventId = :eventId and t.status in :statuses
            """)
    long countTicketsOfCustomerInEvent(Long userId, Long eventId, Collection<TicketStatus> statuses);

    @Query("""
            select new org.group10.ticket.repository.ReservedSectorPlaces(t.eventSectorId, count(t))
            from Ticket t
            where t.order.id = :orderId
              and t.seatId is null
              and t.status = org.group10.ticket.entity.TicketStatus.RESERVED
            group by t.eventSectorId
            """)
    List<ReservedSectorPlaces> countReservedPlacesWithoutSeatOfOrder(Long orderId);

    @Query("select t from Ticket t join fetch t.order where t.id = :id and t.order.userId = :userId")
    Optional<Ticket> findTicketOfCustomerWithItsOrder(Long id, Long userId);

    @Modifying
    @Query("""
            update Ticket t
            set t.status = org.group10.ticket.entity.TicketStatus.REFUNDED, t.version = t.version + 1
            where t.id = :id and t.status = org.group10.ticket.entity.TicketStatus.ISSUED
            """)
    int markIssuedTicketAsRefunded(Long id);

    @Modifying
    @Query("""
            update Ticket t
            set t.status = org.group10.ticket.entity.TicketStatus.VOIDED, t.version = t.version + 1
            where t.order.id = :orderId and t.status = org.group10.ticket.entity.TicketStatus.RESERVED
            """)
    int voidReservedTicketsOfOrder(Long orderId);
}
