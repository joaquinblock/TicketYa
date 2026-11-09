package org.group10.event.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.SeatBatchRequest;
import org.group10.event.dto.SeatResponse;
import org.group10.event.dto.SeatRowRequest;
import org.group10.event.dto.SectorCreateRequest;
import org.group10.event.dto.SectorResponse;
import org.group10.event.dto.SectorUpdateRequest;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.Seat;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.mapper.SectorMapper;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.SeatRepository;
import org.group10.event.repository.SectorRepository;
import org.group10.event.repository.SectorSeatCount;
import org.group10.event.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Sectors of a venue and the seats of the numbered ones. Seats only exist inside a sector, so both are
 * handled here as a single aggregate.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SectorService {

    private final VenueRepository venueRepository;
    private final SectorRepository sectorRepository;
    private final SeatRepository seatRepository;
    private final EventSectorRepository eventSectorRepository;

    public List<SectorResponse> findAll(Long venueId) {
        ensureVenueExists(venueId);
        Map<Long, Long> seatsBySector = seatRepository.countBySectorOfVenue(venueId).stream()
                .collect(Collectors.toMap(SectorSeatCount::sectorId, SectorSeatCount::seats));
        return sectorRepository.findByVenueIdOrderByNameAsc(venueId).stream()
                .map(sector -> SectorMapper.toResponse(sector, seatsBySector.getOrDefault(sector.getId(), 0L)))
                .toList();
    }

    public SectorResponse findById(Long venueId, Long sectorId) {
        return toResponse(getSector(venueId, sectorId));
    }

    /**
     * Creates a sector and, if it is numbered and the request has rows, its seats.
     *
     * @throws TicketYaException {@code INVALID_SECTOR_CONFIGURATION} if capacity or rows don't match the type,
     *                           {@code SECTOR_NAME_DUPLICATED} or {@code SEAT_ROW_DUPLICATED}
     */
    @Transactional
    public SectorResponse create(Long venueId, SectorCreateRequest request) {
        Venue venue = venueRepository.findById(venueId).orElseThrow(() -> venueNotFound(venueId));
        boolean numbered = request.numbered();
        List<SeatRowRequest> rows = request.rows() == null ? List.of() : request.rows();
        validateConfiguration(numbered, request.capacity());
        if (!numbered && !rows.isEmpty()) {
            throw invalidConfiguration("Un sector no numerado no tiene asientos: no envíes rows");
        }

        String name = request.name().trim();
        ensureNameAvailable(venueId, name, null);
        Sector sector = sectorRepository.save(SectorMapper.toEntity(venue, name, numbered, request.capacity()));
        int seats = saveRows(sector, rows).size();
        return SectorMapper.toResponse(sector, seats);
    }

    /**
     * Renames the sector and, for general admission sectors, changes its capacity.
     *
     * @throws TicketYaException {@code SECTOR_IN_USE} if the capacity changes while an event that already left
     *                           {@code DRAFT} sells the sector
     */
    @Transactional
    public SectorResponse update(Long venueId, Long sectorId, SectorUpdateRequest request) {
        Sector sector = getSector(venueId, sectorId);
        validateConfiguration(sector.isNumbered(), request.capacity());

        String name = request.name().trim();
        ensureNameAvailable(venueId, name, sectorId);
        if (!sector.isNumbered() && !Objects.equals(sector.getCapacity(), request.capacity())) {
            ensureStructureEditable(sector);
            sector.setCapacity(request.capacity());
        }
        sector.setName(name);
        return toResponse(sector);
    }

    /**
     * Deletes the sector with its seats.
     *
     * @throws TicketYaException {@code SECTOR_IN_USE} if any event, in any status, sells it
     */
    @Transactional
    public void delete(Long venueId, Long sectorId) {
        Sector sector = getSector(venueId, sectorId);
        if (eventSectorRepository.existsBySectorId(sectorId)) {
            throw new TicketYaException(EventErrorCode.SECTOR_IN_USE,
                    "El sector %d se vende en al menos un evento y no se puede eliminar".formatted(sectorId));
        }
        seatRepository.deleteAllBySectorId(sectorId);
        sectorRepository.delete(sector);
    }

    public List<SeatResponse> findSeats(Long venueId, Long sectorId) {
        getSector(venueId, sectorId);
        return seatRepository.findBySectorIdOrderByRowAscNumberAsc(sectorId).stream()
                .map(SectorMapper::toResponse)
                .toList();
    }

    /**
     * Adds whole rows to a numbered sector. Each row gets seats numbered from 1.
     *
     * @return the created seats
     * @throws TicketYaException {@code SECTOR_NOT_NUMBERED}, {@code SECTOR_IN_USE} or {@code SEAT_ROW_DUPLICATED}
     */
    @Transactional
    public List<SeatResponse> addSeatRows(Long venueId, Long sectorId, SeatBatchRequest request) {
        Sector sector = getSector(venueId, sectorId);
        if (!sector.isNumbered()) {
            throw new TicketYaException(EventErrorCode.SECTOR_NOT_NUMBERED,
                    "El sector %d no es numerado: su tamaño se define con capacity".formatted(sectorId));
        }
        ensureStructureEditable(sector);
        return saveRows(sector, request.rows()).stream().map(SectorMapper::toResponse).toList();
    }

    @Transactional
    public void deleteSeat(Long venueId, Long sectorId, Long seatId) {
        Sector sector = getSector(venueId, sectorId);
        Seat seat = seatRepository.findByIdAndSectorId(seatId, sectorId)
                .orElseThrow(() -> new TicketYaException(EventErrorCode.SEAT_NOT_FOUND,
                        "No se encontró el asiento %d en el sector %d".formatted(seatId, sectorId)));
        ensureStructureEditable(sector);
        seatRepository.delete(seat);
    }

    /**
     * Removes every sector and seat of a venue. Only for {@link VenueService#delete}, which checks first that
     * no event uses the venue.
     */
    void deleteAllOfVenue(Long venueId) {
        seatRepository.deleteAllByVenueId(venueId);
        sectorRepository.deleteAllByVenueId(venueId);
    }

    private List<Seat> saveRows(Sector sector, List<SeatRowRequest> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> labels = rows.stream().map(row -> normalizeRow(row.row())).toList();
        ensureRowsAvailable(sector.getId(), labels);

        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String label = labels.get(i);
            IntStream.rangeClosed(1, rows.get(i).seats())
                    .forEach(number -> seats.add(Seat.builder().sector(sector).row(label).number(number).build()));
        }
        return seatRepository.saveAll(seats);
    }

    private void ensureRowsAvailable(Long sectorId, List<String> labels) {
        Set<String> seen = new HashSet<>();
        List<String> repeated = labels.stream().filter(label -> !seen.add(label)).distinct().toList();
        if (!repeated.isEmpty()) {
            throw rowsDuplicated("Hay filas repetidas en el pedido", repeated);
        }
        List<String> existing = seatRepository.findExistingRows(sectorId, labels);
        if (!existing.isEmpty()) {
            throw rowsDuplicated("Esas filas ya existen en el sector %d".formatted(sectorId), existing);
        }
    }

    /**
     * Once an event leaves {@code DRAFT} the ticket-service builds its inventory from the sector (one
     * {@code event_seat} per seat, or a stock counter from the capacity), so changing it would leave both
     * services out of sync.
     */
    private void ensureStructureEditable(Sector sector) {
        if (eventSectorRepository.existsBySectorIdAndEventStatusNot(sector.getId(), EventStatus.DRAFT)) {
            throw new TicketYaException(EventErrorCode.SECTOR_IN_USE, ("El sector %d se usa en un evento que ya no "
                    + "está en borrador: no se puede cambiar su capacidad ni sus asientos").formatted(sector.getId()));
        }
    }

    private void validateConfiguration(boolean numbered, Integer capacity) {
        if (numbered && capacity != null) {
            throw invalidConfiguration("Un sector numerado no lleva capacity: se calcula con sus asientos");
        }
        if (!numbered && capacity == null) {
            throw invalidConfiguration("Un sector no numerado necesita capacity");
        }
    }

    private void ensureNameAvailable(Long venueId, String name, Long excludedSectorId) {
        boolean taken = excludedSectorId == null
                ? sectorRepository.existsByVenueIdAndNameIgnoreCase(venueId, name)
                : sectorRepository.existsByVenueIdAndNameIgnoreCaseAndIdNot(venueId, name, excludedSectorId);
        if (taken) {
            throw new TicketYaException(EventErrorCode.SECTOR_NAME_DUPLICATED,
                    "Ya existe un sector '%s' en el lugar %d".formatted(name, venueId));
        }
    }

    private SectorResponse toResponse(Sector sector) {
        long seats = sector.isNumbered() ? seatRepository.countBySectorId(sector.getId()) : 0;
        return SectorMapper.toResponse(sector, seats);
    }

    private Sector getSector(Long venueId, Long sectorId) {
        ensureVenueExists(venueId);
        return sectorRepository.findByIdAndVenueId(sectorId, venueId)
                .orElseThrow(() -> new TicketYaException(EventErrorCode.SECTOR_NOT_FOUND,
                        "No se encontró el sector %d en el lugar %d".formatted(sectorId, venueId)));
    }

    private void ensureVenueExists(Long venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw venueNotFound(venueId);
        }
    }

    // the seat unique key is case-insensitive under MySQL's default collation, so "a" and "A" would collide
    private static String normalizeRow(String row) {
        return row.trim().toUpperCase(Locale.ROOT);
    }

    private static TicketYaException venueNotFound(Long venueId) {
        return new TicketYaException(EventErrorCode.VENUE_NOT_FOUND,
                "No se encontró el lugar con id %d".formatted(venueId));
    }

    private static TicketYaException invalidConfiguration(String message) {
        return new TicketYaException(EventErrorCode.INVALID_SECTOR_CONFIGURATION, message);
    }

    private static TicketYaException rowsDuplicated(String message, List<String> rows) {
        return new TicketYaException(EventErrorCode.SEAT_ROW_DUPLICATED, message, Map.of("rows", rows));
    }
}
