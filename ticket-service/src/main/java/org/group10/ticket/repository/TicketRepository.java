package org.group10.ticket.repository;

import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByOrderIdOrderById(Long orderId);

    List<Ticket> findByOrderIdInOrderById(Collection<Long> orderIds);

    @Query("""
            select new org.group10.ticket.repository.EventSectorReportAggregate(
                t.eventSectorId,
                count(case when t.status in (
                    org.group10.ticket.entity.TicketStatus.ISSUED,
                    org.group10.ticket.entity.TicketStatus.USED
                ) then t.id else null end),
                count(case when t.status = org.group10.ticket.entity.TicketStatus.REFUNDED
                    then t.id else null end),
                sum(case when t.status in (
                    org.group10.ticket.entity.TicketStatus.ISSUED,
                    org.group10.ticket.entity.TicketStatus.USED
                ) then t.price else null end)
            )
            from Ticket t
            where t.order.eventId = :eventId
              and t.status in (
                  org.group10.ticket.entity.TicketStatus.ISSUED,
                  org.group10.ticket.entity.TicketStatus.USED,
                  org.group10.ticket.entity.TicketStatus.REFUNDED
              )
            group by t.eventSectorId
            """)
    List<EventSectorReportAggregate> findReportAggregatesByEventId(Long eventId);

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

    @Query("select t from Ticket t join fetch t.order where t.qrCode = :qrCode")
    Optional<Ticket> findWithOrderByQrCode(String qrCode);

    /**
     * Marca la entrada como usada solo si sigue {@code ISSUED}: si dos lectores la escanean a la vez, uno actualiza
     * la fila y el otro recibe 0.
     */
    @Modifying
    @Query("""
            update Ticket t
            set t.status = org.group10.ticket.entity.TicketStatus.USED, t.usedAt = :usedAt,
                t.validatedBy = :validatedBy, t.version = t.version + 1
            where t.id = :id and t.status = org.group10.ticket.entity.TicketStatus.ISSUED
            """)
    int markIssuedTicketAsUsed(Long id, LocalDateTime usedAt, Long validatedBy);

    @Query("""
            select t from Ticket t join fetch t.order o
            where t.status in (org.group10.ticket.entity.TicketStatus.ISSUED, org.group10.ticket.entity.TicketStatus.USED)
              and o.eventId in (
                  select s.eventId from EventSale s
                  where s.status = org.group10.ticket.entity.EventSaleStatus.CANCELLED)
            order by t.id
            """)
    List<Ticket> findTicketsToRefundOfCancelledEvents();

    @Modifying
    @Query("""
            update Ticket t
            set t.status = org.group10.ticket.entity.TicketStatus.REFUNDED, t.version = t.version + 1
            where t.id = :id
              and t.status in (org.group10.ticket.entity.TicketStatus.ISSUED, org.group10.ticket.entity.TicketStatus.USED)
            """)
    int markSoldTicketAsRefunded(Long id);

    @Modifying
    @Query("""
            update Ticket t
            set t.status = org.group10.ticket.entity.TicketStatus.VOIDED, t.version = t.version + 1
            where t.order.id = :orderId and t.status = org.group10.ticket.entity.TicketStatus.RESERVED
            """)
    int voidReservedTicketsOfOrder(Long orderId);
}
