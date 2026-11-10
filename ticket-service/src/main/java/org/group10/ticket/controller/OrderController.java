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
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.dto.ReservationRequest;
import org.group10.ticket.service.OrderClosingService;
import org.group10.ticket.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Órdenes", description = "Reserva temporal de lugares")
public class OrderController {

    private static final String USER_HEADER = "X-User-Id";

    private final ReservationService reservationService;
    private final OrderClosingService orderClosingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reservar lugares", description = "Crea una orden PENDING que retiene los lugares durante 10 minutos.")
    @ApiResponse(responseCode = "201", description = "Reserva creada, o la orden ya creada por ese mismo intento")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "resource_not_found: el evento, sector o asiento no existe",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "seat_unavailable, sector_sold_out, ticket_limit_exceeded, event_not_on_sale, sale_not_started o data_conflict",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrderResponse reserve(@RequestHeader(USER_HEADER) @Positive Long userId,
                                 @Valid @RequestBody ReservationRequest request) {
        return reservationService.reserve(userId, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Abandonar una reserva", description = "Libera los lugares en el momento. Repetirlo sobre una orden abandonada responde con éxito.")
    @ApiResponse(responseCode = "200", description = "Orden abandonada")
    @ApiResponse(responseCode = "404", description = "order_not_found: no existe o es de otro cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "payment_in_progress, reservation_expired u order_not_cancellable",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrderResponse cancel(@RequestHeader(USER_HEADER) @Positive Long userId, @PathVariable @Positive Long id) {
        return orderClosingService.cancelReservation(userId, id);
    }
}
