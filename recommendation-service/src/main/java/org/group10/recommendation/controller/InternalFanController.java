package org.group10.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.recommendation.service.FanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/fans")
@RequiredArgsConstructor
@Tag(name = "Fans (interno)", description = "Clientes con un artista favorito en una ciudad, solo entre servicios")
public class InternalFanController {

    private final FanService fanService;

    @GetMapping
    @Operation(summary = "Ver los fans de unos participantes en una ciudad",
            description = "Lo usa el notification-service al publicarse un evento (US-NOT-03). Devuelve los ids de los "
                    + "clientes que tienen como favorito, elegido o inferido de sus compras, a alguno de los "
                    + "participantes y guardaron esa ciudad (sin distinguir mayúsculas ni acentos).")
    @ApiResponse(responseCode = "200", description = "Ids de los clientes, de menor a mayor; vacío si no hay")
    @ApiResponse(responseCode = "400", description = "validation_error: sin participantes, id no positivo o sin ciudad",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: falta el secreto interno o es incorrecto",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<Long> findFans(@RequestParam @NotEmpty List<@NotNull @Positive Long> participantIds,
                               @RequestParam @NotBlank String city) {
        return fanService.findFans(participantIds, city);
    }
}
