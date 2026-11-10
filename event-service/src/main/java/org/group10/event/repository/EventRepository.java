package org.group10.event.repository;

import org.group10.event.entity.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsByVenueId(Long venueId);

    boolean existsByParticipantsId(Long participantId);

    /**
     * The events with their participants, in one query: for a page of search results.
     */
    @EntityGraph(attributePaths = "participants")
    List<Event> findWithParticipantsByIdIn(Collection<Long> ids);

    @Query("""
            select e.id from Event e
            where e.status in (org.group10.event.entity.EventStatus.PUBLISHED,
                               org.group10.event.entity.EventStatus.SOLD_OUT)
              and e.dateTime <= :now
            order by e.id
            """)
    List<Long> findIdsOfOnSaleEventsThatAlreadyHappened(LocalDateTime now);

    @Modifying
    @Query("""
            update Event e set e.status = org.group10.event.entity.EventStatus.FINISHED
            where e.id = :id
              and e.status in (org.group10.event.entity.EventStatus.PUBLISHED,
                               org.group10.event.entity.EventStatus.SOLD_OUT)
              and e.dateTime <= :now
            """)
    int finishIfStillOnSaleAndAlreadyHappened(Long id, LocalDateTime now);
}
