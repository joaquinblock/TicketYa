package org.group10.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Anulación de un intento de pago sin resultado")
public record PaymentCancelRequest(
        @Schema(description = "Clave del intento de pago", example = "9f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f")
        @NotBlank @Size(max = 255)
        String idempotencyKey
) {}
