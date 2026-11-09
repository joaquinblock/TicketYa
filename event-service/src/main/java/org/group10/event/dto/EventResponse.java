package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;

import java.time.LocalDateTime;

@Schema(description = "Evento con su lugar")
public record EventResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Los Serranos en vivo") String name,
        @Schema(example = "Presentación del nuevo disco") String description,
        @Schema(example = "CONCERT") EventType type,
        @Schema(description = "Organización en el auth-service", example = "1") Long organizationId,
        @Schema(description = "Organizador que lo creó, en el auth-service", example = "2") Long createdBy,
        VenueSummaryResponse venue,
        @Schema(example = "2026-12-12T21:00:00") LocalDateTime dateTime,
        @Schema(example = "DRAFT") EventStatus status,
        @Schema(example = "true") boolean highDemand,
        @Schema(example = "2026-11-20T10:00:00") LocalDateTime saleStart,
        @Schema(example = "4") Integer maxTicketsPerCustomer
) {}
