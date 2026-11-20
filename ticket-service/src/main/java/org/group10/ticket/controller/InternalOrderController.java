package org.group10.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.ticket.dto.OrderResponse;
import org.group10.ticket.service.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
@Tag(name = "Órdenes (interno)", description = "Órdenes y sus entradas, solo entre servicios")
public class InternalOrderController {

    private final OrderQueryService orderQueryService;

    @GetMapping("/{id}")
    @Operation(summary = "Ver una orden pagada",
            description = "Lo usa el notification-service para el mail de la compra: los datos del evento y las "
                    + "entradas con su QR. Solo órdenes PAID.")
    @ApiResponse(responseCode = "200", description = "Orden pagada con sus entradas")
    @ApiResponse(responseCode = "400", description = "validation_error: id no positivo",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: falta el secreto interno o es incorrecto",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "order_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "order_not_paid: la orden no está PAID",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrderResponse findPaidOrder(@PathVariable @Positive Long id) {
        return orderQueryService.findPaidOrderDetail(id);
    }
}
