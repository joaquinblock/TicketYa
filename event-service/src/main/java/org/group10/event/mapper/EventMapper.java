package org.group10.event.mapper;

import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventDetailResponse;
import org.group10.event.dto.EventResponse;
import org.group10.event.dto.EventSectorResponse;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.Venue;

import java.util.Comparator;
import java.util.List;

public final class EventMapper {

    private EventMapper() {
    }

    /**
     * Builds a new event in {@code DRAFT}, which is where every event starts.
     */
    public static Event toEntity(EventCreateRequest request, Venue venue, Long organizationId, Long organizerId) {
        return Event.builder()
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .type(request.type())
                .organizationId(organizationId)
                .createdBy(organizerId)
                .venue(venue)
                .dateTime(request.dateTime())
                .status(EventStatus.DRAFT)
                .highDemand(request.highDemand())
                .saleStart(request.saleStart())
                .maxTicketsPerCustomer(request.maxTicketsPerCustomer())
                .build();
    }

    /**
     * Replaces the data of the request. Organization, creator, status, sectors and participants don't change here.
     */
    public static void update(Event event, EventCreateRequest request, Venue venue) {
        event.setName(request.name().trim());
        event.setDescription(trimToNull(request.description()));
        event.setType(request.type());
        event.setVenue(venue);
        event.setDateTime(request.dateTime());
        event.setHighDemand(request.highDemand());
        event.setSaleStart(request.saleStart());
        event.setMaxTicketsPerCustomer(request.maxTicketsPerCustomer());
    }

    public static EventResponse toResponse(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getDescription(), event.getType(),
                event.getOrganizationId(), event.getCreatedBy(), VenueMapper.toSummary(event.getVenue()),
                event.getDateTime(), event.getStatus(), event.isHighDemand(), event.getSaleStart(),
                event.getMaxTicketsPerCustomer());
    }

    /**
     * The capacity is the sum of the sectors the event sells, the same rule as {@code VenueMapper.toDetail} but only
     * with the event's sectors (see "Capacidades" in {@code event-service.md}).
     */
    public static EventDetailResponse toDetail(Event event, List<EventSectorResponse> sectors) {
        List<EventSectorResponse> sortedSectors = sectors.stream()
                .sorted(Comparator.comparing(EventSectorResponse::name)).toList();
        List<ParticipantResponse> participants = event.getParticipants().stream()
                .map(ParticipantMapper::toResponse)
                .sorted(Comparator.comparing(ParticipantResponse::name))
                .toList();
        int capacity = sectors.stream().mapToInt(EventSectorResponse::capacity).sum();
        return new EventDetailResponse(event.getId(), event.getName(), event.getDescription(), event.getType(),
                event.getOrganizationId(), event.getCreatedBy(), VenueMapper.toSummary(event.getVenue()),
                event.getDateTime(), event.getStatus(), event.isHighDemand(), event.getSaleStart(),
                event.getMaxTicketsPerCustomer(), capacity, sortedSectors, participants);
    }

    private static String trimToNull(String text) {
        return text == null ? null : text.trim();
    }
}
