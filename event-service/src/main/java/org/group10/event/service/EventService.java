package org.group10.event.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.client.dto.OrganizerResponse;
import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.mapper.EventMapper;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final AuthClient authClient;

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

    private static TicketYaException invalidConfiguration(String message) {
        return new TicketYaException(EventErrorCode.INVALID_EVENT_CONFIGURATION, message);
    }
}
