package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos de un lugar, para crearlo o reemplazarlos")
public record VenueRequest(
        @Schema(description = "Nombre del lugar", example = "Estadio del Sur")
        @NotBlank @Size(max = 255)
        String name,

        @Schema(description = "Dirección", example = "Av. del Estadio 1000")
        @NotBlank @Size(max = 255)
        String address,

        @Schema(description = "Ciudad; se usa en la búsqueda de eventos", example = "Buenos Aires")
        @NotBlank @Size(max = 255)
        String city,

        @Schema(description = "Latitud en grados decimales", example = "-34.5453")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
        Double latitude,

        @Schema(description = "Longitud en grados decimales", example = "-58.4498")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
        Double longitude
) {}
