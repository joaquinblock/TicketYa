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
        EventOfOrder event,
        @Schema(example = "PENDING") OrderStatus status,
        @Schema(description = "Vencimiento de la reserva", example = "2026-11-10T10:42:00") LocalDateTime expiresAt,
        @Schema(example = "140000.00") BigDecimal total,
        @Schema(description = "Medio del pago aprobado", example = "CREDIT_CARD") PaymentMethod paymentMethod,
        List<TicketResponse> tickets
) {

    @Schema(description = "Datos del evento para mostrar en la entrada")
    public record EventOfOrder(
            @Schema(example = "Los Serranos en vivo") String name,
            @Schema(description = "Fecha vigente del evento, con la reprogramación si la hubo",
                    example = "2026-12-13T21:00:00") LocalDateTime dateTime,
            @Schema(example = "Estadio del Sur") String venueName,
            @Schema(example = "Av. del Estadio 1000") String venueAddress,
            @Schema(example = "Buenos Aires") String venueCity
    ) {
    }
}
