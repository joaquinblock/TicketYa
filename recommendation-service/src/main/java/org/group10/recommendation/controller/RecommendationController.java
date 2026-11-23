package org.group10.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.common.security.Caller;
import org.group10.recommendation.dto.EventSummary;
import org.group10.recommendation.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations/me")
@RequiredArgsConstructor
@Tag(name = "Sugerencias", description = "Eventos sugeridos según las preferencias y las compras del cliente")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    @Operation(summary = "Ver mis sugerencias",
            description = "Solo clientes. Hasta 10 eventos que se pueden comprar, como los de la búsqueda, sin los que "
                    + "ya compró. Primero los de sus artistas favoritos, sus tipos de evento y su ciudad, y los que "
                    + "compraron otros clientes que compraron lo mismo que él. Sin preferencias ni compras, los próximos "
                    + "eventos.")
    @ApiResponse(responseCode = "200", description = "Eventos sugeridos, del más al menos recomendado")
    @ApiResponse(responseCode = "403", description = "forbidden: el usuario no es cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: event-service no responde",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<EventSummary> suggest(Caller caller) {
        return recommendationService.suggest(caller);
    }
}
