package org.group10.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Pago de una orden, con el detalle para resolver reclamos")
public record OrderPaymentResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "10") Long orderId,
        @Schema(example = "25000.00") BigDecimal amount,
        @Schema(example = "DEBIT_CARD") PaymentMethod paymentMethod,
        @Schema(example = "REJECTED") PaymentStatus status,
        @Schema(description = "Motivo del rechazo, solo si fue rechazado", example = "Fondos insuficientes")
        String failureReason,
        @Schema(description = "Identificador asignado por la pasarela", example = "MOCK-1b4e28ba-2fa1-31d2-883f-0016d3cca427")
        String gatewayId,
        @Schema(example = "2026-11-10T10:32:47") LocalDateTime createdAt
) {}
