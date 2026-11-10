package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Schema(description = "Participantes del evento. Reemplaza la lista completa; una lista vacía los quita a todos")
public record EventParticipantsRequest(
        @ArraySchema(schema = @Schema(example = "1"))
        @NotNull
        List<@NotNull @Positive Long> participantIds
) {}
