package org.group10.event.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.client.TicketClient;
import org.group10.event.client.dto.EventSaleRequest;
import org.group10.event.client.dto.OrganizerResponse;
import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventDetailResponse;
import org.group10.event.dto.EventParticipantsRequest;
import org.group10.event.dto.EventResponse;
import org.group10.event.dto.EventSectorResponse;
import org.group10.event.dto.EventSectorsRequest;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.Participant;
import org.group10.event.entity.Seat;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.mapper.EventMapper;
import org.group10.event.mapper.ParticipantMapper;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.ParticipantRepository;
import org.group10.event.repository.SeatRepository;
import org.group10.event.repository.SectorRepository;
import org.group10.event.repository.SectorSeatCount;
import org.group10.event.repository.VenueRepository;
import org.group10.event.statemachine.EventStatusMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final ParticipantRepository participantRepository;
    private final EventSectorRepository eventSectorRepository;
    private final SectorRepository sectorRepository;
    private final SeatRepository seatRepository;
    private final EventStatusMachine statusMachine;
    private final TransactionOperations transactions;
    private final AuthClient authClient;
    private final TicketClient ticketClient;

    /**
     * The event with its venue, the sectors it sells with their price and capacity, and its participants.
     *
     * @throws TicketYaException {@code EVENT_NOT_FOUND}
     */
    public EventDetailResponse findById(Long id) {
        return toDetail(getEvent(id));
    }

    /**
     * Replaces the data of a draft event. If the venue changes, the sectors it sold are removed, because they belong
     * to the old venue.
     * <p>
     * The checks against the auth-service run outside the transaction, like in {@link #create}; only the write runs
     * inside one.
     *
     * @throws TicketYaException {@code EVENT_NOT_FOUND}, {@code EVENT_NOT_EDITABLE}, {@code VENUE_NOT_FOUND},
     *                           {@code INVALID_EVENT_CONFIGURATION} or {@code ORGANIZER_NOT_IN_ORGANIZATION}
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EventDetailResponse update(Long id, Long organizerId, EventCreateRequest request) {
        Event event = getEvent(id);
        ensureDraft(event, "editar");
        validateSaleWindow(request);
        if (!venueRepository.existsById(request.venueId())) {
            throw new TicketYaException(EventErrorCode.VENUE_NOT_FOUND,
                    "No se encontró el lugar con id %d".formatted(request.venueId()));
        }
        verifyOrganizer(event.getOrganizationId(), organizerId);

        return transactions.execute(tx -> {
            Event managed = getEvent(id);
            ensureDraft(managed, "editar");
            if (!managed.getVenue().getId().equals(request.venueId())) {
                eventSectorRepository.deleteAllByEventId(id);
            }
            EventMapper.update(managed, request, venueRepository.getReferenceById(request.venueId()));
            eventRepository.flush();
            return toDetail(managed);
        });
    }

    /**
     * Replaces the sectors a draft event sells and their prices. Each one has to be a sector of the event's venue, and
     * none can repeat, because it would have two prices. An empty list removes them all.
     * <p>
     * Updated in place: a sector that stays keeps its {@code event_sector} id and only its price changes, so the id
     * stays stable if one day prices can change after publishing (the ticket-service refers to it).
     *
     * @throws TicketYaException {@code EVENT_NOT_FOUND}, {@code EVENT_NOT_EDITABLE},
     *                           {@code INVALID_EVENT_CONFIGURATION} with the repeated ids in {@code detail},
     *                           {@code ORGANIZER_NOT_IN_ORGANIZATION} or {@code SECTOR_NOT_FOUND} with the ids that
     *                           aren't sectors of the venue in {@code detail}
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EventDetailResponse replaceSectors(Long id, Long organizerId, EventSectorsRequest request) {
        Event event = getEvent(id);
        ensureDraft(event, "cambiar los sectores");
        List<Long> repeated = request.sectors().stream()
                .collect(Collectors.groupingBy(EventSectorsRequest.SectorPrice::sectorId, Collectors.counting()))
                .entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).sorted().toList();
        if (!repeated.isEmpty()) {
            throw new TicketYaException(EventErrorCode.INVALID_EVENT_CONFIGURATION,
                    "Cada sector va una sola vez, con un solo precio: se repiten %s".formatted(repeated),
                    Map.of("repeatedSectorIds", repeated));
        }
        verifyOrganizer(event.getOrganizationId(), organizerId);

        return transactions.execute(tx -> {
            Event managed = getEvent(id);
            ensureDraft(managed, "cambiar los sectores");
            Long venueId = managed.getVenue().getId();
            List<Long> ids = request.sectors().stream().map(EventSectorsRequest.SectorPrice::sectorId).toList();
            Map<Long, Sector> sectors = sectorRepository.findAllById(ids).stream()
                    .filter(s -> s.getVenue().getId().equals(venueId))
                    .collect(Collectors.toMap(Sector::getId, Function.identity()));
            List<Long> missing = ids.stream().filter(sectorId -> !sectors.containsKey(sectorId)).toList();
            if (!missing.isEmpty()) {
                throw new TicketYaException(EventErrorCode.SECTOR_NOT_FOUND,
                        "Los sectores %s no son del lugar %d del evento".formatted(missing, venueId),
                        Map.of("missingIds", missing));
            }

            Map<Long, EventSector> current = eventSectorRepository.findByEventId(id).stream()
                    .collect(Collectors.toMap(es -> es.getSector().getId(), Function.identity()));
            eventSectorRepository.deleteAll(current.values().stream()
                    .filter(es -> !ids.contains(es.getSector().getId()))
                    .toList());
            List<EventSector> added = new ArrayList<>();
            for (EventSectorsRequest.SectorPrice requested : request.sectors()) {
                BigDecimal price = requested.price().setScale(2);   // @Digits allows at most 2 decimals
                EventSector kept = current.get(requested.sectorId());
                if (kept != null) {
                    kept.setPrice(price);
                } else {
                    added.add(EventSector.builder().event(managed).sector(sectors.get(requested.sectorId()))
                            .price(price).build());
                }
            }
            eventSectorRepository.saveAll(added);
            return toDetail(managed);
        });
    }

    /**
     * Deletes a draft event with its sectors and participants. A draft has no tickets yet: its inventory is created
     * when it's published. A published event is cancelled, not deleted.
     *
     * @throws TicketYaException {@code EVENT_NOT_FOUND}, {@code EVENT_NOT_EDITABLE} or
     *                           {@code ORGANIZER_NOT_IN_ORGANIZATION}
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void delete(Long id, Long organizerId) {
        Event event = getEvent(id);
        ensureDraft(event, "borrar");
        verifyOrganizer(event.getOrganizationId(), organizerId);

        transactions.execute(tx -> {
            ensureDraft(getEvent(id), "borrar");
            eventSectorRepository.deleteAllByEventId(id);
            eventRepository.deleteById(id);
            return null;
        });
    }

    /**
     * Publishes a draft: {@code DRAFT -> PUBLISHED} through the state machine, once it can be sold.
     *
     * @throws TicketYaException {@code EVENT_NOT_FOUND}, {@code INVALID_STATUS_TRANSITION} if it isn't a draft,
     *                           {@code ORGANIZER_NOT_IN_ORGANIZATION} or {@code EVENT_NOT_PUBLISHABLE} with every
     *                           failed precondition in {@code detail.problems}
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EventDetailResponse publish(Long id, Long organizerId) {
        Event event = getEvent(id);
        ensureStatus(event, EventStatus.DRAFT, "Solo se publica un borrador");
        verifyOrganizer(event.getOrganizationId(), organizerId);

        EventSaleRequest sale = transactions.execute(tx -> {
            Event managed = getEvent(id);
            ensureStatus(managed, EventStatus.DRAFT, "Solo se publica un borrador");
            List<String> problems = publishProblems(managed, sectorsOf(managed));
            if (!problems.isEmpty()) {
                throw new TicketYaException(EventErrorCode.EVENT_NOT_PUBLISHABLE,
                        "El evento %d todavía no se puede publicar".formatted(id), Map.of("problems", problems));
            }
            return saleSnapshotOf(managed, EventStatus.PUBLISHED);
        });
        ticketClient.synchronizeEventSale(id, sale);

        return transactions.execute(tx -> {
            Event managed = getEvent(id);
            ensureStatus(managed, EventStatus.DRAFT, "Solo se publica un borrador");
            managed.changeStatus(EventStatus.PUBLISHED, statusMachine);
            return toDetail(managed);
        });
    }

    /**
     * Cancels a published, sold out or suspended event for good. Any administrator can cancel any event.
     *
     * @throws TicketYaException {@code EVENT_NOT_FOUND} or {@code INVALID_STATUS_TRANSITION} for a draft (it's
     *                           deleted instead) or a finished event
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EventDetailResponse cancel(Long id) {
        Event event = getEvent(id);
        ensureNotStatus(event, EventStatus.DRAFT, "Un borrador no se cancela: se borra con DELETE /api/events/%d"
                .formatted(id));
        statusMachine.transition(event.getStatus(), EventStatus.CANCELLED);

        ticketClient.synchronizeEventSale(id, transactions.execute(tx ->
                saleSnapshotOf(getEvent(id), EventStatus.CANCELLED)));

        return transactions.execute(tx -> {
            Event managed = getEvent(id);
            managed.changeStatus(EventStatus.CANCELLED, statusMachine);
            return toDetail(managed);
        });
    }

    @Transactional
    public void markSoldOut(Long id) {
        getEvent(id).changeStatus(EventStatus.SOLD_OUT, statusMachine);
    }

    /**
     * Replaces the participants of a draft event with the given ones. Repeated ids count once, and an empty list
     * removes them all.
     * <p>
     * Runs without a surrounding transaction, like {@link #create}, so the call to the auth-service doesn't hold a
     * database connection.
     *
     * @param organizerId user making the change; has to organize for the event's organization
     * @return the event's participants, ordered by name
     * @throws TicketYaException {@code EVENT_NOT_FOUND}, {@code EVENT_NOT_EDITABLE} if it isn't a draft,
     *                           {@code ORGANIZER_NOT_IN_ORGANIZATION} or {@code PARTICIPANT_NOT_FOUND} with the
     *                           missing ids in {@code detail}
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ParticipantResponse> replaceParticipants(Long eventId, Long organizerId,
                                                         EventParticipantsRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new TicketYaException(EventErrorCode.EVENT_NOT_FOUND,
                        "No se encontró el evento con id %d".formatted(eventId)));
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new TicketYaException(EventErrorCode.EVENT_NOT_EDITABLE,
                    "El evento %d está %s: solo se le pueden cambiar los participantes en DRAFT"
                            .formatted(eventId, event.getStatus()));
        }
        verifyOrganizer(event.getOrganizationId(), organizerId);

        Set<Long> ids = new LinkedHashSet<>(request.participantIds());
        List<Participant> participants = participantRepository.findAllById(ids);
        if (participants.size() != ids.size()) {
            Set<Long> found = participants.stream().map(Participant::getId).collect(Collectors.toSet());
            List<Long> missing = ids.stream().filter(id -> !found.contains(id)).toList();
            throw new TicketYaException(EventErrorCode.PARTICIPANT_NOT_FOUND,
                    "No se encontraron los participantes %s".formatted(missing), Map.of("missingIds", missing));
        }

        event.setParticipants(new HashSet<>(participants));
        eventRepository.save(event);
        return participants.stream()
                .sorted(Comparator.comparing(Participant::getName))
                .map(ParticipantMapper::toResponse)
                .toList();
    }

    /**
     * Creates an event in {@code DRAFT} for an organization, on behalf of one of its organizers.
     * <p>
     * Runs without a surrounding transaction so the calls to the auth-service don't hold a database connection
     * while they wait; the venue lookup and the insert each use their own.
     *
     * @param organizationId organization the event belongs to, validated against the auth-service
     * @param organizerId    user creating it; has to be an active organizer of that organization
     * @throws TicketYaException {@code INVALID_EVENT_CONFIGURATION}, {@code VENUE_NOT_FOUND} or
     *                           {@code ORGANIZER_NOT_IN_ORGANIZATION}; the auth-service's
     *                           {@code organization_not_found} and {@code organizer_not_found} reach the caller as is
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EventResponse create(Long organizationId, Long organizerId, EventCreateRequest request) {
        validateSaleWindow(request);
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new TicketYaException(EventErrorCode.VENUE_NOT_FOUND,
                        "No se encontró el lugar con id %d".formatted(request.venueId())));
        verifyOrganizer(organizationId, organizerId);

        Event event = eventRepository.save(EventMapper.toEntity(request, venue, organizationId, organizerId));
        return EventMapper.toResponse(event);
    }

    /**
     * The virtual queue of a high demand event opens when the sale starts, so it needs a {@code saleStart};
     * and the sale can't open once the event has already started.
     */
    private void validateSaleWindow(EventCreateRequest request) {
        if (request.highDemand() && request.saleStart() == null) {
            throw invalidConfiguration("Un evento de alta demanda necesita saleStart: la fila virtual abre en ese momento");
        }
        if (request.saleStart() != null && !request.saleStart().isBefore(request.dateTime())) {
            throw invalidConfiguration("saleStart tiene que ser anterior a la fecha del evento");
        }
    }

    /**
     * Checks both ids against the auth-service. A missing organization or organizer comes back as a 404 that
     * the common exception handler forwards unchanged.
     */
    private void verifyOrganizer(Long organizationId, Long organizerId) {
        authClient.getOrganization(organizationId);
        OrganizerResponse organizer = authClient.getOrganizer(organizerId);
        if (!organizationId.equals(organizer.organizationId())) {
            throw new TicketYaException(EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION,
                    "El organizador %d no pertenece a la organización %d".formatted(organizerId, organizationId));
        }
    }

    /**
     * Every precondition that fails, so the organizer can fix them all at once.
     */
    private static List<String> publishProblems(Event event, List<EventSectorResponse> sectors) {
        List<String> problems = new ArrayList<>();
        if (sectors.isEmpty()) {
            problems.add("No tiene sectores a la venta");
        }
        sectors.stream()
                .filter(s -> s.numbered() && s.capacity() == 0)
                .forEach(s -> problems.add("El sector numerado '%s' no tiene asientos".formatted(s.name())));
        if (!event.getDateTime().isAfter(LocalDateTime.now())) {
            problems.add("La fecha del evento ya pasó");
        }
        if (event.isHighDemand() && event.getSaleStart() == null) {
            problems.add("Es de alta demanda y no tiene saleStart");
        }
        if (event.getSaleStart() != null && !event.getSaleStart().isBefore(event.getDateTime())) {
            problems.add("saleStart tiene que ser anterior a la fecha del evento");
        }
        return problems;
    }

    private EventSaleRequest saleSnapshotOf(Event event, EventStatus status) {
        List<EventSector> sectors = eventSectorRepository.findByEventId(event.getId());
        List<Long> numberedSectorIds = sectors.stream()
                .map(EventSector::getSector)
                .filter(Sector::isNumbered)
                .map(Sector::getId)
                .toList();
        Map<Long, List<Long>> seatIdsBySector = seatRepository.findBySectorIdIn(numberedSectorIds).stream()
                .collect(Collectors.groupingBy(seat -> seat.getSector().getId(),
                        Collectors.mapping(Seat::getId, Collectors.toList())));

        return new EventSaleRequest(status, event.getDateTime(), event.getSaleStart(), event.isHighDemand(),
                event.getMaxTicketsPerCustomer(), sectors.stream()
                .map(eventSector -> saleOfSector(eventSector, seatIdsBySector))
                .toList());
    }

    private static EventSaleRequest.SectorSale saleOfSector(EventSector eventSector,
                                                            Map<Long, List<Long>> seatIdsBySector) {
        Sector sector = eventSector.getSector();
        return sector.isNumbered()
                ? new EventSaleRequest.SectorSale(eventSector.getId(), eventSector.getPrice(), null,
                        seatIdsBySector.getOrDefault(sector.getId(), List.of()))
                : new EventSaleRequest.SectorSale(eventSector.getId(), eventSector.getPrice(), sector.getCapacity(), null);
    }

    private EventDetailResponse toDetail(Event event) {
        return EventMapper.toDetail(event, sectorsOf(event));
    }

    /**
     * A numbered sector's capacity is its number of seats; a general admission one's is the loaded capacity.
     */
    private List<EventSectorResponse> sectorsOf(Event event) {
        Map<Long, Long> seatsBySector = seatRepository.countBySectorOfVenue(event.getVenue().getId()).stream()
                .collect(Collectors.toMap(SectorSeatCount::sectorId, SectorSeatCount::seats));
        return eventSectorRepository.findByEventId(event.getId()).stream()
                .map(es -> {
                    Sector sector = es.getSector();
                    int capacity = sector.isNumbered()
                            ? seatsBySector.getOrDefault(sector.getId(), 0L).intValue()
                            : sector.getCapacity();
                    return new EventSectorResponse(sector.getId(), sector.getName(), sector.isNumbered(), capacity,
                            es.getPrice());
                })
                .toList();
    }

    private Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new TicketYaException(EventErrorCode.EVENT_NOT_FOUND,
                        "No se encontró el evento con id %d".formatted(id)));
    }

    private static void ensureDraft(Event event, String action) {
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new TicketYaException(EventErrorCode.EVENT_NOT_EDITABLE,
                    "El evento %d está %s: solo se puede %s en DRAFT".formatted(event.getId(), event.getStatus(), action));
        }
    }

    private static void ensureStatus(Event event, EventStatus expected, String message) {
        if (event.getStatus() != expected) {
            throw new TicketYaException(EventErrorCode.INVALID_STATUS_TRANSITION,
                    "%s: el evento %d está %s".formatted(message, event.getId(), event.getStatus()));
        }
    }

    private static void ensureNotStatus(Event event, EventStatus forbidden, String message) {
        if (event.getStatus() == forbidden) {
            throw new TicketYaException(EventErrorCode.INVALID_STATUS_TRANSITION, message);
        }
    }

    private static TicketYaException invalidConfiguration(String message) {
        return new TicketYaException(EventErrorCode.INVALID_EVENT_CONFIGURATION, message);
    }
}
