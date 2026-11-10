package org.group10.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.event.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
@Tag(name = "Eventos internos", description = "Cambios de estado que pide el ticket-service")
public class InternalEventController {

    private final EventService eventService;

    @PutMapping("/{id}/sold-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marcar un evento como agotado", description = "Lo llama el ticket-service. Repetirlo no cambia nada.")
    @ApiResponse(responseCode = "204", description = "Evento agotado")
    @ApiResponse(responseCode = "404", description = "event_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "invalid_status_transition",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void markSoldOut(@PathVariable @Positive Long id) {
        eventService.markSoldOut(id);
    }
}
