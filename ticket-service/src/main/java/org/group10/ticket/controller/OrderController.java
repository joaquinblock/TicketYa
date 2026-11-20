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
import org.group10.common.security.Caller;
import org.group10.common.security.Permission;
import org.group10.common.security.RequiresPermission;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.dto.PayOrderRequest;
import org.group10.ticket.dto.ReservationRequest;
import org.group10.ticket.service.OrderClosingService;
import org.group10.ticket.service.OrderPaymentService;
import org.group10.ticket.service.OrderQueryService;
import org.group10.ticket.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Órdenes", description = "Reserva temporal de lugares")
@ApiResponse(responseCode = "401", description = "unauthorized: sin token",
        content = @Content(schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "forbidden: sin TICKET_PURCHASE",
        content = @Content(schema = @Schema(implementation = ErrorDto.class)))
public class OrderController {

    private final ReservationService reservationService;
    private final OrderClosingService orderClosingService;
    private final OrderPaymentService orderPaymentService;
    private final OrderQueryService orderQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.TICKET_PURCHASE)
    @Operation(summary = "Reservar lugares", description = "Crea una orden PENDING que retiene los lugares durante 10 minutos.")
    @ApiResponse(responseCode = "201", description = "Reserva creada, o la orden ya creada por ese mismo intento")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "resource_not_found: el evento, sector o asiento no existe",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "seat_unavailable, sector_sold_out, ticket_limit_exceeded, event_not_on_sale, sale_not_started o data_conflict",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrderResponse reserve(Caller caller, @Valid @RequestBody ReservationRequest request) {
        return reservationService.reserve(caller.userId(), request);
    }

    @PostMapping("/{id}/pay")
    @RequiresPermission(Permission.TICKET_PURCHASE)
    @Operation(summary = "Pagar una orden", description = "Cobra el total de la orden y emite las entradas. Repetir un intento no cobra dos veces.")
    @ApiResponse(responseCode = "200", description = "Orden pagada, con sus entradas y QR")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "order_not_found: no existe o es de otro cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "reservation_expired, payment_window_closed, order_not_payable o payment_in_progress",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "payment_rejected, con reason y rejectedAttempts en detail",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: el pago quedó sin resultado y solo se puede repetir ese intento",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrderResponse pay(Caller caller, @PathVariable @Positive Long id, @Valid @RequestBody PayOrderRequest request) {
        return orderPaymentService.pay(caller.userId(), id, request);
    }

    @GetMapping
    @RequiresPermission(Permission.TICKET_PURCHASE)
    @Operation(summary = "Ver mis compras", description = "El QR solo aparece en las entradas ISSUED.")
    @ApiResponse(responseCode = "200", description = "Órdenes del cliente, de la más nueva a la más vieja")
    public List<OrderResponse> findMyOrders(Caller caller) {
        return orderQueryService.findOrdersOfCustomer(caller.userId());
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.TICKET_PURCHASE)
    @Operation(summary = "Ver una compra")
    @ApiResponse(responseCode = "200", description = "Orden con sus entradas")
    @ApiResponse(responseCode = "404", description = "order_not_found: no existe o es de otro cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrderResponse findMyOrder(Caller caller, @PathVariable @Positive Long id) {
        return orderQueryService.findOrderDetailOfCustomer(caller.userId(), id);
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission(Permission.TICKET_PURCHASE)
    @Operation(summary = "Abandonar una reserva", description = "Libera los lugares en el momento. Repetirlo sobre una orden abandonada responde con éxito.")
    @ApiResponse(responseCode = "200", description = "Orden abandonada")
    @ApiResponse(responseCode = "404", description = "order_not_found: no existe o es de otro cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "payment_in_progress, reservation_expired u order_not_cancellable",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrderResponse cancel(Caller caller, @PathVariable @Positive Long id) {
        return orderClosingService.cancelReservation(caller.userId(), id);
    }
}
