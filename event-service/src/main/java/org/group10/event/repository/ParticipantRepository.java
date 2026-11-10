package org.group10.event.repository;

import org.group10.event.entity.Participant;
import org.group10.event.entity.ParticipantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    /**
     * Participants ordered by name. Both filters are optional: a {@code null} one doesn't filter.
     *
     * @param name part of the name, case-insensitive
     */
    @Query("""
            select p from Participant p
            where (:type is null or p.type = :type)
              and (:name is null or lower(p.name) like lower(concat('%', :name, '%')))
            order by p.name""")
    List<Participant> search(ParticipantType type, String name);

    boolean existsByNameIgnoreCaseAndType(String name, ParticipantType type);

    boolean existsByNameIgnoreCaseAndTypeAndIdNot(String name, ParticipantType type, Long id);
}
