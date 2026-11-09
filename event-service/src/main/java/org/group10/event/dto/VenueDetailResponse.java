package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Lugar con sus sectores y su capacidad total")
public record VenueDetailResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Estadio del Sur") String name,
        @Schema(example = "Av. del Estadio 1000") String address,
        @Schema(example = "Buenos Aires") String city,
        @Schema(example = "-34.5453") Double latitude,
        @Schema(example = "-58.4498") Double longitude,
        @Schema(description = "Suma de la capacidad de todos sus sectores (calculada, no se guarda)", example = "5080")
        int capacity,
        List<SectorResponse> sectors
) {}
