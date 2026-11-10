package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.event.entity.ParticipantType;

@Schema(description = "Artista, equipo o disertante")
public record ParticipantResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Los Serranos") String name,
        @Schema(example = "ARTIST") ParticipantType type
) {}
