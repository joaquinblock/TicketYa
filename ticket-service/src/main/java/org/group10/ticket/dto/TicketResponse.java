package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.ticket.entity.TicketStatus;

import java.math.BigDecimal;

@Schema(description = "Entrada de una orden. En un sector no numerado, seatId, row y number vienen en null")
public record TicketResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "2") Long eventSectorId,
        @Schema(example = "Platea") String sectorName,
        @Schema(description = "Asiento, solo en sectores numerados", example = "15") Long seatId,
        @Schema(description = "Fila, solo en sectores numerados", example = "A") String row,
        @Schema(description = "Número de asiento, solo en sectores numerados", example = "15") Integer number,
        @Schema(example = "70000.00") BigDecimal price,
        @Schema(example = "ISSUED") TicketStatus status,
        @Schema(description = "Solo en entradas ISSUED", example = "3f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f") String qrCode
) {
}
