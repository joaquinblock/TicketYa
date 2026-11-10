package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.group10.ticket.entity.EventSaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Datos de venta de un evento, con su inventario completo")
public record EventSaleRequest(
        @NotNull @Schema(example = "PUBLISHED") EventSaleStatus status,
        @NotBlank @Size(max = 255) @Schema(example = "Los Serranos en vivo") String eventName,
        @NotBlank @Size(max = 255) @Schema(example = "Estadio del Sur") String venueName,
        @NotBlank @Size(max = 255) @Schema(example = "Av. del Estadio 1000") String venueAddress,
        @NotBlank @Size(max = 255) @Schema(example = "Buenos Aires") String venueCity,
        @NotNull @Schema(example = "2026-12-20T21:00:00") LocalDateTime dateTime,
        @Schema(example = "2026-11-20T10:00:00") LocalDateTime saleStart,
        @Schema(example = "false") boolean highDemand,
        @Positive @Schema(example = "4") Integer maxTicketsPerCustomer,
        @NotNull @Valid List<SectorSale> sectors
) {

    @Schema(description = "Un sector a la venta: numerado con seats o no numerado con capacity")
    public record SectorSale(
            @NotNull @Positive @Schema(example = "1") Long eventSectorId,
            @NotBlank @Size(max = 255) @Schema(example = "Platea") String sectorName,
            @NotNull @Positive @Schema(example = "45000.00") BigDecimal price,
            @Positive @Schema(example = "5000") Integer capacity,
            List<@NotNull @Valid SeatSale> seats
    ) {

        @Schema(hidden = true)
        @AssertTrue(message = "Un sector lleva capacity o seats, no los dos")
        public boolean isNumberedOrWithCapacity() {
            return (capacity == null) != (seats == null);
        }
    }

    @Schema(description = "Un asiento de un sector numerado")
    public record SeatSale(
            @NotNull @Positive @Schema(example = "15") Long seatId,
            @NotBlank @Size(max = 10) @Schema(example = "F") String row,
            @NotNull @Positive @Schema(example = "12") Integer number
    ) {
    }
}
