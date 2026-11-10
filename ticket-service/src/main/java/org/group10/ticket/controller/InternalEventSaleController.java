package org.group10.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.ticket.dto.EventSaleRequest;
import org.group10.ticket.service.EventSaleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/event-sales")
@RequiredArgsConstructor
@Tag(name = "Venta de eventos", description = "Inventario y datos de venta, solo entre servicios")
public class InternalEventSaleController {

    private final EventSaleService eventSaleService;

    @PutMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sincronizar la venta de un evento", description = "Lo llama event-service. Crea solo los lugares que faltan y actualiza estado, fechas y precios.")
    @ApiResponse(responseCode = "204", description = "Venta sincronizada")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void synchronizeEventSale(@PathVariable @Positive Long eventId, @Valid @RequestBody EventSaleRequest request) {
        eventSaleService.synchronizeEventSale(eventId, request);
    }
}
