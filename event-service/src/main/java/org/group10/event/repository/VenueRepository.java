package org.group10.event.repository;

import org.group10.event.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    List<Venue> findAllByOrderByNameAsc();

    List<Venue> findByCityIgnoreCaseOrderByNameAsc(String city);
}
