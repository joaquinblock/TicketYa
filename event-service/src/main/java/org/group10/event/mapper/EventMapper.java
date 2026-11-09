package org.group10.event.mapper;

import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.Venue;

public final class EventMapper {

    private EventMapper() {
    }

    /**
     * Builds a new event in {@code DRAFT}, which is where every event starts.
     */
    public static Event toEntity(EventCreateRequest request, Venue venue, Long organizationId, Long organizerId) {
        return Event.builder()
                .name(request.name().trim())
                .description(request.description() == null ? null : request.description().trim())
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

    public static EventResponse toResponse(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getDescription(), event.getType(),
                event.getOrganizationId(), event.getCreatedBy(), VenueMapper.toSummary(event.getVenue()),
                event.getDateTime(), event.getStatus(), event.isHighDemand(), event.getSaleStart(),
                event.getMaxTicketsPerCustomer());
    }
}
