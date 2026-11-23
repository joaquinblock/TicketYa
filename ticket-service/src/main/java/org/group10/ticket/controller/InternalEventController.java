package org.group10.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.ticket.service.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
@Tag(name = "Eventos (interno)", description = "Compras de un evento, solo entre servicios")
public class InternalEventController {

    private final OrderQueryService orderQueryService;

    @GetMapping("/{eventId}/buyers")
    @Operation(summary = "Ver los compradores de un evento",
            description = "Lo usa el notification-service para avisar que el evento se canceló, se suspendió o se "
                    + "reprogramó. Devuelve los ids de los usuarios con alguna orden PAID, sin repetir.")
    @ApiResponse(responseCode = "200", description = "Ids de los compradores; vacío si no hay")
    @ApiResponse(responseCode = "400", description = "validation_error: id no positivo",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: falta el secreto interno o es incorrecto",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<Long> findBuyers(@PathVariable @Positive Long eventId) {
        return orderQueryService.findBuyersOfEvent(eventId);
    }
}
