package org.group10.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Un evento sugerido, con los mismos campos que un resultado de {@code GET /api/events} para que el front use la
 * misma tarjeta. Es una copia propia del contrato de event-service, que también sirve para leer su búsqueda.
 * {@code type} y {@code status} van como texto: un valor nuevo en event-service no rompe la lectura.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventSummary(
        @Schema(example = "1") Long id,
        @Schema(example = "Los Serranos en vivo") String name,
        @Schema(example = "CONCERT") String type,
        @Schema(description = "PUBLISHED o SOLD_OUT", example = "PUBLISHED") String status,
        @Schema(example = "2026-12-12T21:00:00") LocalDateTime dateTime,
        @Schema(example = "true") boolean highDemand,
        @Schema(description = "Organización en el auth-service", example = "1") Long organizationId,
        Venue venue,
        @Schema(description = "Precio del sector más barato", example = "45000.00") BigDecimal priceFrom,
        @Schema(description = "Nombres de los participantes, en orden alfabético") List<String> participants
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Venue(
            @Schema(example = "1") Long id,
            @Schema(example = "Estadio del Sur") String name,
            @Schema(example = "Av. del Estadio 1000") String address,
            @Schema(example = "Buenos Aires") String city,
            @Schema(example = "-34.5453") Double latitude,
            @Schema(example = "-58.4498") Double longitude
    ) {
    }
}
