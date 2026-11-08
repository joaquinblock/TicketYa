package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lugar sin sus sectores")
public record VenueSummaryResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Estadio del Sur") String name,
        @Schema(example = "Av. del Estadio 1000") String address,
        @Schema(example = "Buenos Aires") String city,
        @Schema(example = "-34.5453") Double latitude,
        @Schema(example = "-58.4498") Double longitude
) {}
