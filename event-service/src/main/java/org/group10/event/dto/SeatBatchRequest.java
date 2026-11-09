package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Filas nuevas para agregar a un sector numerado")
public record SeatBatchRequest(
        @NotEmpty @Valid @Size(max = MAX_ROWS)
        List<SeatRowRequest> rows
) {
    public static final int MAX_ROWS = 100;
    public static final int MAX_SEATS_PER_ROW = 200;
}
