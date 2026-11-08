package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sector de un lugar")
public record SectorResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Platea") String name,
        @Schema(example = "true") boolean numbered,
        @Schema(description = "No numerado: la capacidad cargada. Numerado: la cantidad de asientos", example = "80")
        int capacity
) {}
