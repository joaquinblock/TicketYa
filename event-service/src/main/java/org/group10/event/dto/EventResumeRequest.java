package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;

import java.time.LocalDateTime;

@Schema(description = "Opcional: la fecha nueva, si el evento se reprogramó. Sin ella, mantiene la que tenía")
public record EventResumeRequest(
        @Schema(description = "Fecha y hora nuevas; tiene que ser futura y posterior a saleStart",
                example = "2027-02-20T21:00:00")
        @Future
        LocalDateTime dateTime
) {}
