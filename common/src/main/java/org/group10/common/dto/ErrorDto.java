package org.group10.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Formato único de error de todos los microservicios")
public record ErrorDto(
        @Schema(description = "Código del error en snake_case", example = "event_not_found")
        String error,
        @Schema(description = "Mensaje legible para el usuario", example = "No se encontró el evento con id 7")
        String message,
        @Schema(description = "Status HTTP", example = "404")
        Integer status,
        @Schema(description = "Datos extra del error, por ejemplo los campos inválidos")
        Map<String, Object> detail
) {}
