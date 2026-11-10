package org.group10.ticket.repository;

import org.group10.ticket.entity.EventSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventSaleRepository extends JpaRepository<EventSale, Long> {

    @Modifying
    @Query("""
            update EventSale s
            set s.status = org.group10.ticket.entity.EventSaleStatus.SOLD_OUT,
                s.soldOutNotificationPending = true
            where s.eventId = :eventId and s.status = org.group10.ticket.entity.EventSaleStatus.PUBLISHED
            """)
    int markPublishedSaleAsSoldOut(Long eventId);

    @Query("select s.eventId from EventSale s where s.soldOutNotificationPending = true")
    List<Long> findEventIdsWithPendingSoldOutNotification();

    @Modifying
    @Query("update EventSale s set s.soldOutNotificationPending = false where s.eventId = :eventId")
    int clearSoldOutNotification(Long eventId);
}
