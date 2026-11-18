package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Usuario cargado para el que se emite el token")
public record DevLoginRequest(
        @Schema(example = "2") @NotNull @Positive Long userId
) {}
