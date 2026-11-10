package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Evento en un resultado de búsqueda")
public record EventSummaryResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Los Serranos en vivo") String name,
        @Schema(example = "CONCERT") EventType type,
        @Schema(description = "PUBLISHED o SOLD_OUT", example = "PUBLISHED") EventStatus status,
        @Schema(example = "2026-12-12T21:00:00") LocalDateTime dateTime,
        @Schema(example = "true") boolean highDemand,
        @Schema(description = "Organización en el auth-service", example = "1") Long organizationId,
        VenueSummaryResponse venue,
        @Schema(description = "Precio del sector más barato", example = "45000.00") BigDecimal priceFrom,
        @Schema(description = "Nombres de los participantes, en orden alfabético") List<String> participants
) {}
