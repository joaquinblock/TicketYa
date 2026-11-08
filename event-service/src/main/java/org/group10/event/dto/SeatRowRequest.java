package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Una fila de asientos numerados del 1 a seats")
public record SeatRowRequest(
        @Schema(description = "Identificador de la fila; se guarda en mayúsculas", example = "A")
        @NotBlank @Size(max = 10)
        String row,

        @Schema(description = "Cantidad de asientos de la fila", example = "20")
        @NotNull @Min(1) @Max(SeatBatchRequest.MAX_SEATS_PER_ROW)
        Integer seats
) {}
