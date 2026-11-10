package org.group10.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;

import java.math.BigDecimal;

@Schema(description = "Pago de una orden")
public record PaymentResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "10") Long orderId,
        @Schema(example = "25000.00") BigDecimal amount,
        @Schema(example = "CREDIT_CARD") PaymentMethod paymentMethod,
        @Schema(example = "APPROVED") PaymentStatus status,
        @Schema(description = "Identificador asignado por la pasarela", example = "MOCK-1b4e28ba-2fa1-31d2-883f-0016d3cca427")
        String gatewayId
) {}
