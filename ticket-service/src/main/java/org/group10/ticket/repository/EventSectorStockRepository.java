package org.group10.ticket.repository;

import org.group10.ticket.entity.EventSectorStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventSectorStockRepository extends JpaRepository<EventSectorStock, Long> {

    List<EventSectorStock> findByEventId(Long eventId);

    @Modifying
    @Query("""
            update EventSectorStock s
            set s.taken = s.taken + :places
            where s.eventSectorId = :eventSectorId and s.taken + :places <= s.capacity
            """)
    int takePlacesIfTheyFit(Long eventSectorId, int places);

    @Modifying
    @Query("update EventSectorStock s set s.taken = s.taken - :places where s.eventSectorId = :eventSectorId")
    int releasePlaces(Long eventSectorId, long places);
}
