package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

@Schema(description = "Reserva de asientos numerados (seatIds) o de lugares en un sector no numerado (eventSectorId y quantity)")
public record ReservationRequest(
        @NotNull @Positive @Schema(example = "1") Long eventId,
        @NotBlank @Size(max = 255) @Schema(example = "e3b0c442-98fc-4b1d-8f3a-7c5d1a2b3c4d") String idempotencyKey,
        List<@NotNull @Positive Long> seatIds,
        @Positive @Schema(example = "1") Long eventSectorId,
        @Positive @Schema(example = "2") Integer quantity
) {

    @Schema(hidden = true)
    @AssertTrue(message = "Se reservan seatIds, o eventSectorId con quantity, pero no los dos")
    public boolean isSeatsOrSectorChosen() {
        boolean sectorChosen = eventSectorId != null && quantity != null;
        boolean nothingOfTheSectorSent = eventSectorId == null && quantity == null;
        boolean seatsSent = seatIds != null && !seatIds.isEmpty();
        return seatsSent ? nothingOfTheSectorSent : seatIds == null && sectorChosen;
    }

    @Schema(hidden = true)
    @AssertTrue(message = "No se puede repetir un asiento")
    public boolean isEverySeatDifferent() {
        return seatIds == null || new HashSet<>(seatIds).size() == seatIds.size();
    }

    public boolean choosesSeats() {
        return seatIds != null;
    }

    public int requestedTickets() {
        return choosesSeats() ? seatIds.size() : quantity;
    }
}
