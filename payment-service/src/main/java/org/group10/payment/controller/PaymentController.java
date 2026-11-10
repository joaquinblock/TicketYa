package org.group10.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.payment.dto.OrderPaymentResponse;
import org.group10.payment.dto.PaymentRequest;
import org.group10.payment.dto.PaymentResponse;
import org.group10.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Cobros de las órdenes, solo entre servicios")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cobrar una orden", description = "Lo llama ticket-service. El gateway no reenvía esta ruta.")
    @ApiResponse(responseCode = "201", description = "Pago aprobado")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "data_conflict: la clave ya se usó con otros datos o la orden ya tiene otro pago PENDING o APPROVED",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "422", description = "payment_rejected, con reason y rejectedAttempts en detail",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "payment_gateway_unavailable: la pasarela no respondió y se puede reintentar con la misma clave",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public PaymentResponse charge(@Valid @RequestBody PaymentRequest request) {
        return paymentService.charge(request);
    }

    @GetMapping
    @Operation(summary = "Consultar los pagos de una orden", description = "Misma respuesta que GET /api/payments, para las consultas entre servicios.")
    @ApiResponse(responseCode = "200", description = "Pagos de la orden, del más antiguo al más nuevo")
    @ApiResponse(responseCode = "400", description = "invalid_parameter o validation_error: falta orderId o no es válido",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "payment_not_found: la orden no tiene pagos",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<OrderPaymentResponse> findPaymentsOfOrder(@RequestParam @Positive Long orderId) {
        return paymentService.findPaymentsOfOrder(orderId);
    }
}
