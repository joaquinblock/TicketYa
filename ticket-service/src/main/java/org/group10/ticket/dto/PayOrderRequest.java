package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.group10.ticket.entity.PaymentMethod;

@Schema(description = "Intento de pago de una orden")
public record PayOrderRequest(
        @NotNull @Schema(example = "CREDIT_CARD") PaymentMethod paymentMethod,
        @NotBlank @Size(max = 255) @Schema(example = "9f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f") String idempotencyKey
) {
}
