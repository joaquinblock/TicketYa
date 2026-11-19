package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Cliente que pasa a organizar eventos para la organización")
public record AddOrganizerRequest(
        @Schema(example = "7") @NotNull @Positive Long userId
) {}
