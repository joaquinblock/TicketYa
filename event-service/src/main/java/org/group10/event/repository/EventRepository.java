package org.group10.event.repository;

import org.group10.event.entity.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
