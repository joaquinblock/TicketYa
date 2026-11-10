package org.group10.ticket.repository;

import org.group10.ticket.entity.EventSeat;
import org.group10.ticket.entity.EventSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface EventSeatRepository extends JpaRepository<EventSeat, EventSeatId> {

    List<EventSeat> findByEventIdAndSeatIdInOrderBySeatId(Long eventId, Collection<Long> seatIds);

    List<EventSeat> findByEventIdOrderBySeatId(Long eventId);

    @Query("select s.seatId from EventSeat s where s.eventId = :eventId")
    Set<Long> findSeatIdsOfEvent(Long eventId);

    @Modifying
    @Query("""
            update EventSeat s
            set s.status = org.group10.ticket.entity.EventSeatStatus.AVAILABLE,
                s.order = null,
                s.reservedUntil = null,
                s.version = s.version + 1
            where s.order.id = :orderId and s.status = org.group10.ticket.entity.EventSeatStatus.RESERVED
            """)
    int releaseSeatsHeldByOrder(Long orderId);
}
