package org.group10.ticket.repository;

import org.group10.ticket.entity.EventSectorPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventSectorPriceRepository extends JpaRepository<EventSectorPrice, Long> {

    List<EventSectorPrice> findByEventIdOrderByEventSectorId(Long eventId);
}
