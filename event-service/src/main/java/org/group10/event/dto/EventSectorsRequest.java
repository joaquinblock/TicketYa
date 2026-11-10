package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Sectores que vende el evento y su precio. Reemplaza la lista completa; una lista vacía los quita a todos")
public record EventSectorsRequest(
        @NotNull @Valid
        List<@NotNull SectorPrice> sectors
) {

    @Schema(description = "Un sector del lugar del evento con su precio")
    public record SectorPrice(
            @Schema(description = "Sector del lugar", example = "3")
            @NotNull @Positive
            Long sectorId,

            @Schema(description = "Precio de la entrada, mayor a 0 y con hasta dos decimales", example = "20000.00")
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2)
            BigDecimal price
    ) {}
}
