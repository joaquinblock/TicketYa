package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Entrada que acaba de ingresar: el personal ve el sector y el asiento")
public record CheckInResponse(
        @Schema(example = "1") Long ticketId,
        @Schema(example = "Platea") String sectorName,
        @Schema(example = "A", description = "null en un sector sin numerar") String row,
        @Schema(example = "15", description = "null en un sector sin numerar") Integer number,
        @Schema(example = "2026-12-13T20:41:00") LocalDateTime usedAt) {
}
