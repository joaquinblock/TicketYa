package org.group10.ticket.repository;

import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.EventSaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventSaleRepository extends JpaRepository<EventSale, Long> {

    @Modifying
    @Query("""
            update EventSale s
            set s.status = org.group10.ticket.entity.EventSaleStatus.SOLD_OUT,
                s.availabilityNotificationPending = true
            where s.eventId = :eventId and s.status = org.group10.ticket.entity.EventSaleStatus.PUBLISHED
            """)
    int markPublishedSaleAsSoldOut(Long eventId);

    @Modifying
    @Query("""
            update EventSale s
            set s.status = org.group10.ticket.entity.EventSaleStatus.PUBLISHED,
                s.availabilityNotificationPending = true
            where s.eventId = :eventId and s.status = org.group10.ticket.entity.EventSaleStatus.SOLD_OUT
            """)
    int markSoldOutSaleAsPublished(Long eventId);

    List<EventSale> findByAvailabilityNotificationPendingTrue();

    @Modifying
    @Query("""
            update EventSale s set s.availabilityNotificationPending = false
            where s.eventId = :eventId and s.status = :notifiedStatus
            """)
    int clearAvailabilityNotification(Long eventId, EventSaleStatus notifiedStatus);
}
