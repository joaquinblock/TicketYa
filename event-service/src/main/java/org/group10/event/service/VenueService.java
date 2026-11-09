package org.group10.event.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.VenueDetailResponse;
import org.group10.event.dto.VenueRequest;
import org.group10.event.dto.VenueSummaryResponse;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.mapper.VenueMapper;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final SectorService sectorService;

    /**
     * Lists venues ordered by name, optionally only those of a city (case-insensitive).
     */
    public List<VenueSummaryResponse> findAll(String city) {
        List<Venue> venues = city == null || city.isBlank()
                ? venueRepository.findAllByOrderByNameAsc()
                : venueRepository.findByCityIgnoreCaseOrderByNameAsc(city.trim());
        return venues.stream().map(VenueMapper::toSummary).toList();
    }

    public VenueDetailResponse findById(Long id) {
        return VenueMapper.toDetail(getVenue(id), sectorService.findAll(id));
    }

    @Transactional
    public VenueSummaryResponse create(VenueRequest request) {
        return VenueMapper.toSummary(venueRepository.save(VenueMapper.toEntity(request)));
    }

    @Transactional
    public VenueSummaryResponse update(Long id, VenueRequest request) {
        Venue venue = getVenue(id);
        VenueMapper.update(venue, request);
        return VenueMapper.toSummary(venue);
    }

    /**
     * Deletes the venue with its sectors and seats.
     *
     * @throws TicketYaException {@code VENUE_IN_USE} if any event, in any status, takes place there
     */
    @Transactional
    public void delete(Long id) {
        Venue venue = getVenue(id);
        if (eventRepository.existsByVenueId(id)) {
            throw new TicketYaException(EventErrorCode.VENUE_IN_USE,
                    "El lugar %d tiene eventos y no se puede eliminar".formatted(id));
        }
        sectorService.deleteAllOfVenue(id);
        venueRepository.delete(venue);
    }

    private Venue getVenue(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new TicketYaException(EventErrorCode.VENUE_NOT_FOUND,
                        "No se encontró el lugar con id %d".formatted(id)));
    }
}
