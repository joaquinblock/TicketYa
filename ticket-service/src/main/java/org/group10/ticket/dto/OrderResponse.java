package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Orden con sus entradas")
public record OrderResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "1") Long eventId,
        @Schema(example = "PENDING") OrderStatus status,
        @Schema(description = "Vencimiento de la reserva", example = "2026-11-10T10:42:00") LocalDateTime expiresAt,
        @Schema(example = "140000.00") BigDecimal total,
        @Schema(description = "Medio del pago aprobado", example = "CREDIT_CARD") PaymentMethod paymentMethod,
        List<TicketResponse> tickets
) {
}
