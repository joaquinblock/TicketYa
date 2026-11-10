package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.group10.event.entity.EventType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filtros de {@code GET /api/events}. Todos son opcionales; los que no vienen no filtran.
 */
public record EventSearchRequest(
        @Schema(description = "Texto libre: busca en el nombre, los participantes y la descripción, sin distinguir mayúsculas",
                example = "serranos")
        @Size(max = 100)
        String q,

        @Schema(description = "Tipo de evento", example = "CONCERT")
        EventType type,

        @Schema(description = "Ciudad del lugar, completa y sin distinguir mayúsculas ni acentos", example = "Córdoba")
        @Size(max = 255)
        String city,

        @Schema(description = "Parte del nombre de un artista, equipo o disertante", example = "serranos")
        @Size(max = 255)
        String participant,

        @Schema(description = "Organización que publica el evento (id del auth-service)", example = "1")
        @Positive
        Long organizationId,

        @Schema(description = "Desde este día, inclusive", example = "2026-12-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Schema(description = "Hasta este día, inclusive", example = "2027-01-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        @Schema(description = "Orden: DATE (por defecto), PRICE o RELEVANCE", example = "DATE")
        EventSort sort,

        @Schema(description = "Página, desde 0", example = "0")
        @Min(0)
        Integer page,

        @Schema(description = "Eventos por página, de 1 a 50 (por defecto 20)", example = "20")
        @Min(1) @Max(50)
        Integer size
) {

    public static final int DEFAULT_SIZE = 20;

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    public EventSort sortOrDefault() {
        return sort == null ? EventSort.DATE : sort;
    }
}
