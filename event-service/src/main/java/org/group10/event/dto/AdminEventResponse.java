package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;

import java.time.LocalDateTime;

@Schema(description = "Evento en el listado del administrador: cualquier estado salvo DRAFT")
public record AdminEventResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Los Serranos en vivo") String name,
        @Schema(example = "CONCERT") EventType type,
        @Schema(example = "SUSPENDED") EventStatus status,
        @Schema(example = "2026-12-12T21:00:00") LocalDateTime dateTime,
        @Schema(example = "Estadio del Sur") String venueName,
        @Schema(example = "Buenos Aires") String city,
        @Schema(description = "Organización en el auth-service", example = "1") Long organizationId
) {

    public static AdminEventResponse from(Event event) {
        return new AdminEventResponse(event.getId(), event.getName(), event.getType(), event.getStatus(),
                event.getDateTime(), event.getVenue().getName(), event.getVenue().getCity(),
                event.getOrganizationId());
    }
}
