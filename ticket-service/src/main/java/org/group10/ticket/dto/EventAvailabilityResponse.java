package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Lugares libres de un evento, por sector")
public record EventAvailabilityResponse(
        @Schema(example = "1") Long eventId,
        List<SectorAvailability> sectors
) {

    @Schema(description = "Disponibilidad de un sector: seats si es numerado, availablePlaces siempre")
    public record SectorAvailability(
            @Schema(example = "1") Long eventSectorId,
            @Schema(example = "Campo") String sectorName,
            @Schema(example = "45000.00") BigDecimal price,
            @Schema(example = "false") boolean numbered,
            @Schema(example = "4998") int availablePlaces,
            List<SeatAvailability> seats
    ) {
    }

    public record SeatAvailability(
            @Schema(example = "15") Long seatId,
            @Schema(example = "A") String row,
            @Schema(example = "15") Integer number,
            @Schema(example = "true") boolean available
    ) {
    }
}
