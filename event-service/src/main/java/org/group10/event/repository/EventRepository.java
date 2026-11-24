package org.group10.event.repository;

import org.group10.event.config.CatalogCaches;
import org.group10.event.entity.Event;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsByVenueId(Long venueId);

    boolean existsByParticipantsId(Long participantId);

    /**
     * The events with their participants, in one query: for a page of search results.
     */
    @EntityGraph(attributePaths = "participants")
    List<Event> findWithParticipantsByIdIn(Collection<Long> ids);

    /**
     * Every event of the organization, in any status, with its venue: for the organizer's backoffice.
     */
    @EntityGraph(attributePaths = "venue")
    List<Event> findByOrganizationIdOrderByDateTimeDescIdDesc(Long organizationId);

    @Query("""
            select e.id from Event e
            where e.status in (org.group10.event.entity.EventStatus.PUBLISHED,
                               org.group10.event.entity.EventStatus.SOLD_OUT)
              and e.dateTime <= :now
            order by e.id
            """)
    List<Long> findIdsOfOnSaleEventsThatAlreadyHappened(LocalDateTime now);

    /**
     * Locks the event row until the transaction ends, so two changes of the same event run one after the other and
     * the second one reads the status the first one left. Call it before reading the event in that transaction.
     */
    @Query(value = "select id from event where id = :id for update", nativeQuery = true)
    Optional<Long> lockById(Long id);

    /**
     * Evicts the event's detail. It runs inside the job's transaction, so the eviction comes a moment before the
     * commit: a read in between would keep the old status until the TTL, on an event that already happened.
     */
    @Modifying
    @CacheEvict(cacheNames = CatalogCaches.EVENT, key = "#id")
    @Query("""
            update Event e set e.status = org.group10.event.entity.EventStatus.FINISHED
            where e.id = :id
              and e.status in (org.group10.event.entity.EventStatus.PUBLISHED,
                               org.group10.event.entity.EventStatus.SOLD_OUT)
              and e.dateTime <= :now
            """)
    int finishIfStillOnSaleAndAlreadyHappened(Long id, LocalDateTime now);
}
