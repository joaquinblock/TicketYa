package org.group10.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = """
        Modificación de un sector. El tipo (numerado o no) no se puede cambiar. \
        capacity va solo en los no numerados; los asientos se modifican con /seats.""")
public record SectorUpdateRequest(
        @Schema(description = "Nombre, único dentro del lugar", example = "Campo VIP")
        @NotBlank @Size(max = 255)
        String name,

        @Schema(description = "Cantidad de lugares; obligatoria en no numerados y prohibida en numerados", example = "4500")
        @Positive
        Integer capacity
) {}
