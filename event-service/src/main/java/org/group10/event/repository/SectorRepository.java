package org.group10.event.repository;

import org.group10.event.entity.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SectorRepository extends JpaRepository<Sector, Long> {

    List<Sector> findByVenueIdOrderByNameAsc(Long venueId);

    Optional<Sector> findByIdAndVenueId(Long id, Long venueId);

    boolean existsByVenueIdAndNameIgnoreCase(Long venueId, String name);

    boolean existsByVenueIdAndNameIgnoreCaseAndIdNot(Long venueId, String name, Long id);

    @Modifying
    @Query("delete from Sector s where s.venue.id = :venueId")
    void deleteAllByVenueId(Long venueId);
}
