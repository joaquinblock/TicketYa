package org.group10.event.repository;

import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventSectorRepository extends JpaRepository<EventSector, Long> {

    boolean existsBySectorId(Long sectorId);

    boolean existsBySectorIdAndEventStatusNot(Long sectorId, EventStatus status);

    /**
     * The sectors an event sells, with the venue's sector loaded (it's lazy).
     */
    @EntityGraph(attributePaths = "sector")
    List<EventSector> findByEventId(Long eventId);

    @Modifying
    @Query("delete from EventSector es where es.event.id = :eventId")
    void deleteAllByEventId(Long eventId);
}
