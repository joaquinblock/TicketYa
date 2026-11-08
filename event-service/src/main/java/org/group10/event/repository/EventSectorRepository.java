package org.group10.event.repository;

import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSectorRepository extends JpaRepository<EventSector, Long> {

    boolean existsBySectorId(Long sectorId);

    boolean existsBySectorIdAndEventStatusNot(Long sectorId, EventStatus status);
}
