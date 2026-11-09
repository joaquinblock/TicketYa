package org.group10.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.event.dto.EventCreateRequest;
import org.group10.event.dto.EventResponse;
import org.group10.event.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * En la Entrega 1 no hay JWT: quien llama se identifica con los headers {@value #USER_HEADER} y
 * {@value #ORGANIZATION_HEADER}, y el service los valida contra el auth-service.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Eventos", description = "Alta y gestión de eventos")
public class EventController {

    static final String USER_HEADER = "X-User-Id";
    static final String ORGANIZATION_HEADER = "X-Organization-Id";

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un evento", description = """
            Se crea en `DRAFT`, sin sectores ni participantes. El usuario de `X-User-Id` tiene que ser un \
            organizador activo de la organización de `X-Organization-Id`.""")
    @ApiResponse(responseCode = "201", description = "Evento creado en borrador")
    @ApiResponse(responseCode = "400", description = "validation_error / invalid_parameter / invalid_event_configuration",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "organizer_not_in_organization",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "venue_not_found / organization_not_found / organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: el auth-service no responde",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventResponse create(
            @Parameter(description = "Organización dueña del evento", example = "1")
            @RequestHeader(ORGANIZATION_HEADER) Long organizationId,
            @Parameter(description = "Organizador que lo crea (id de usuario)", example = "2")
            @RequestHeader(USER_HEADER) Long organizerId,
            @Valid @RequestBody EventCreateRequest request) {
        return eventService.create(organizationId, organizerId, request);
    }
}
