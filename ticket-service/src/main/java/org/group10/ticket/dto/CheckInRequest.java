package org.group10.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "QR escaneado en el ingreso y el evento donde está el personal")
public record CheckInRequest(
        @NotNull @Positive @Schema(example = "1") Long eventId,
        @NotBlank @Size(max = 255) @Schema(example = "859c3438-0059-403e-af24-f76673ca852e") String qrCode
) {
}
