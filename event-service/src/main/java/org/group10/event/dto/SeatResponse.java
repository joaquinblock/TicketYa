package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Asiento de un sector numerado")
public record SeatResponse(
        @Schema(example = "15") Long id,
        @Schema(example = "A") String row,
        @Schema(example = "7") int number
) {}
