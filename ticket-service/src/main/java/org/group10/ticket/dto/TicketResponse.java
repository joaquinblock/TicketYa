package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.ticket.entity.TicketStatus;

import java.math.BigDecimal;

@Schema(description = "Entrada de una orden")
public record TicketResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "2") Long eventSectorId,
        @Schema(description = "Asiento, solo en sectores numerados", example = "15") Long seatId,
        @Schema(example = "70000.00") BigDecimal price,
        @Schema(example = "ISSUED") TicketStatus status,
        @Schema(description = "Solo en entradas ISSUED", example = "3f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f") String qrCode
) {
}
