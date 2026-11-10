package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Una página de la búsqueda. Es un record propio, y no el {@code Page} de Spring Data, para que el JSON no dependa
 * de cómo serializa Spring sus clases internas.
 */
@Schema(description = "Página de resultados")
public record EventPageResponse(
        List<EventSummaryResponse> content,
        @Schema(description = "Página actual, desde 0", example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(description = "Eventos que cumplen los filtros, en todas las páginas", example = "6") long totalElements,
        @Schema(example = "1") int totalPages
) {}
