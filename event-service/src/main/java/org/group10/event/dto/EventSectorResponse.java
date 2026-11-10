package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Sector del lugar que el evento vende, con su precio")
public record EventSectorResponse(
        @Schema(description = "Sector del lugar", example = "2") Long sectorId,
        @Schema(example = "Platea") String name,
        @Schema(example = "true") boolean numbered,
        @Schema(description = "No numerado: la capacidad cargada. Numerado: la cantidad de asientos", example = "80")
        int capacity,
        @Schema(example = "70000.00") BigDecimal price
) {}
