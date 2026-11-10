package org.group10.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.payment.dto.OrderPaymentResponse;
import org.group10.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Consulta de pagos", description = "Pagos de una orden, para resolver reclamos")
public class PaymentQueryController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Consultar los pagos de una orden", description = "Incluye los rechazados.")
    @ApiResponse(responseCode = "200", description = "Pagos de la orden, del más antiguo al más nuevo")
    @ApiResponse(responseCode = "400", description = "invalid_parameter o validation_error: falta orderId o no es válido",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "payment_not_found: la orden no tiene pagos",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<OrderPaymentResponse> findPaymentsOfOrder(@RequestParam @Positive Long orderId) {
        return paymentService.findPaymentsOfOrder(orderId);
    }
}
