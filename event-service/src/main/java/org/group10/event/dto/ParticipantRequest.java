package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.group10.event.entity.ParticipantType;

@Schema(description = "Datos de un participante, para crearlo o reemplazarlos")
public record ParticipantRequest(
        @Schema(description = "Nombre del artista, equipo o disertante", example = "Los Serranos")
        @NotBlank @Size(max = 255)
        String name,

        @Schema(description = "Tipo de participante", example = "ARTIST")
        @NotNull
        ParticipantType type
) {}
