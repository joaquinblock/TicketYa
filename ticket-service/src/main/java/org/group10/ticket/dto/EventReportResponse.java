package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Reporte de ventas de un evento, desglosado por sector")
public record EventReportResponse(
        @Schema(example = "1") Long eventId,
        List<SectorReport> sectors,
        EventTotals total
) {

    @Schema(description = "Métricas de un sector vendido por el evento")
    public record SectorReport(
            @Schema(description = "Identificador del sector del evento")
            Long eventSectorId,
            @Schema(example = "Platea")
            String sectorName,
            @Schema(example = "100")
            int capacity,
            @Schema(description = "Entradas ISSUED o USED", example = "70")
            long soldTickets,
            @Schema(description = "Entradas REFUNDED", example = "2")
            long returnedTickets,
            @Schema(description = "Porcentaje sobre la capacidad, con dos decimales", example = "75.00")
            BigDecimal occupancyPercentage,
            @Schema(description = "Suma de precios no reintegrados", example = "4900000.00")
            BigDecimal revenue
    ) {
    }

    @Schema(description = "Suma de las métricas de todos los sectores")
    public record EventTotals(
            @Schema(example = "100")
            long capacity,
            @Schema(example = "70")
            long soldTickets,
            @Schema(example = "2")
            long returnedTickets,
            @Schema(description = "Porcentaje sobre la capacidad total, con dos decimales", example = "75.00")
            BigDecimal occupancyPercentage,
            @Schema(example = "4900000.00")
            BigDecimal revenue
    ) {
    }
}
