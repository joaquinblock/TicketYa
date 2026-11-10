package org.group10.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.group10.payment.entity.PaymentMethod;

import java.math.BigDecimal;

@Schema(description = "Cobro de una orden")
public record PaymentRequest(
        @Schema(description = "Orden de ticket-service", example = "10")
        @NotNull @Positive
        Long orderId,

        @Schema(description = "Monto a cobrar", example = "25000.00")
        @NotNull @Positive @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @Schema(description = "Medio de pago", example = "CREDIT_CARD")
        @NotNull
        PaymentMethod paymentMethod,

        @Schema(description = "Clave del intento de pago", example = "9f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f")
        @NotBlank @Size(max = 255)
        String idempotencyKey
) {}
