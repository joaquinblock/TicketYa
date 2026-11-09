package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = """
        Alta de un sector. Numerado: sin capacity, con rows opcionales (los asientos se pueden agregar después). \
        No numerado: con capacity y sin rows.""")
public record SectorCreateRequest(
        @Schema(description = "Nombre, único dentro del lugar", example = "Platea")
        @NotBlank @Size(max = 255)
        String name,

        @Schema(description = "true si tiene asientos con fila y número", example = "true")
        @NotNull
        Boolean numbered,

        @Schema(description = "Cantidad de lugares; solo para sectores no numerados", example = "5000")
        @Positive
        Integer capacity,

        @Schema(description = "Filas de asientos a crear; solo para sectores numerados")
        @Valid @Size(max = SeatBatchRequest.MAX_ROWS)
        List<SeatRowRequest> rows
) {}
