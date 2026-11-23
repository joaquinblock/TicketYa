package org.group10.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Reemplaza todas las preferencias del cliente. Lo que no viene queda vacío.
 */
@Schema(description = "Preferencias del cliente: reemplazan las anteriores")
public record PreferencesRequest(
        @Schema(description = "Tipos de evento: CONCERT, FESTIVAL, THEATER, SPORTS o CONFERENCE",
                example = "[\"CONCERT\"]")
        List<@NotNull String> eventTypes,

        @Schema(description = "Ids de participantes favoritos del event-service", example = "[1, 2]")
        List<@NotNull Long> participantIds,

        @Schema(description = "Ciudad del cliente", example = "Tandil")
        String city
) {}
