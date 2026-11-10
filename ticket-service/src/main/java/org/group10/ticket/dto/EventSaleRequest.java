package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.group10.ticket.entity.EventSaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Datos de venta de un evento, con su inventario completo")
public record EventSaleRequest(
        @NotNull @Schema(example = "PUBLISHED") EventSaleStatus status,
        @NotNull @Schema(example = "2026-12-20T21:00:00") LocalDateTime dateTime,
        @Schema(example = "2026-11-20T10:00:00") LocalDateTime saleStart,
        @Schema(example = "false") boolean highDemand,
        @Positive @Schema(example = "4") Integer maxTicketsPerCustomer,
        @NotNull @Valid List<SectorSale> sectors
) {

    @Schema(description = "Un sector a la venta: numerado con seatIds o no numerado con capacity")
    public record SectorSale(
            @NotNull @Positive @Schema(example = "1") Long eventSectorId,
            @NotNull @Positive @Schema(example = "45000.00") BigDecimal price,
            @Positive @Schema(example = "5000") Integer capacity,
            List<@NotNull @Positive Long> seatIds
    ) {

        @Schema(hidden = true)
        @AssertTrue(message = "Un sector lleva capacity o seatIds, no los dos")
        public boolean isNumberedOrWithCapacity() {
            return (capacity == null) != (seatIds == null);
        }
    }
}
