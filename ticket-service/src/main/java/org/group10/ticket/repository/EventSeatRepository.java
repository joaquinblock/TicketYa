package org.group10.ticket.repository;

import org.group10.ticket.entity.EventSeat;
import org.group10.ticket.entity.EventSeatId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSeatRepository extends JpaRepository<EventSeat, EventSeatId> {
}
