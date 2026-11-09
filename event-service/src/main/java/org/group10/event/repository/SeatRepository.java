package org.group10.event.repository;

import org.group10.event.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findBySectorIdOrderByRowAscNumberAsc(Long sectorId);

    Optional<Seat> findByIdAndSectorId(Long id, Long sectorId);

    long countBySectorId(Long sectorId);

    @Query("""
            select new org.group10.event.repository.SectorSeatCount(s.sector.id, count(s))
            from Seat s
            where s.sector.venue.id = :venueId
            group by s.sector.id
            """)
    List<SectorSeatCount> countBySectorOfVenue(Long venueId);

    @Query("select distinct s.row from Seat s where s.sector.id = :sectorId and s.row in :rows")
    List<String> findExistingRows(Long sectorId, Collection<String> rows);

    @Modifying
    @Query("delete from Seat s where s.sector.id = :sectorId")
    void deleteAllBySectorId(Long sectorId);

    @Modifying
    @Query("delete from Seat s where s.sector.id in (select sc.id from Sector sc where sc.venue.id = :venueId)")
    void deleteAllByVenueId(Long venueId);
}
