package org.group10.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.event.dto.ParticipantRequest;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.entity.ParticipantType;
import org.group10.event.service.ParticipantService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Like {@code VenueController}, it has no {@code @RequiresPermission} yet: there is no permission for managing
 * the catalog (see {@code permissions.md}, "Pendiente").
 */
@RestController
@RequestMapping("/api/participants")
@RequiredArgsConstructor
@Tag(name = "Participantes", description = "ABM de artistas, equipos y disertantes")
public class ParticipantController {

    private final ParticipantService participantService;

    @GetMapping
    @Operation(summary = "Listar participantes", description = "Ordenados por nombre. Se pueden filtrar por tipo y por "
            + "parte del nombre.")
    @ApiResponse(responseCode = "200", description = "Participantes encontrados (puede ser una lista vacía)")
    @ApiResponse(responseCode = "400", description = "invalid_parameter: tipo inexistente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<ParticipantResponse> findAll(
            @Parameter(description = "Tipo de participante", example = "ARTIST")
            @RequestParam(required = false) ParticipantType type,
            @Parameter(description = "Parte del nombre, sin distinguir mayúsculas", example = "serranos")
            @RequestParam(required = false) String name) {
        return participantService.findAll(type, name);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un participante")
    @ApiResponse(responseCode = "200", description = "Participante encontrado")
    @ApiResponse(responseCode = "404", description = "participant_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ParticipantResponse findById(@PathVariable Long id) {
        return participantService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un participante", description = "El nombre y el tipo no se pueden repetir, sin "
            + "distinguir mayúsculas.")
    @ApiResponse(responseCode = "201", description = "Participante creado")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "participant_duplicated",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ParticipantResponse create(@Valid @RequestBody ParticipantRequest request) {
        return participantService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar un participante", description = "Reemplaza nombre y tipo. Los eventos que lo "
            + "tienen lo siguen teniendo, con los datos nuevos.")
    @ApiResponse(responseCode = "200", description = "Participante modificado")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "participant_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "participant_duplicated",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ParticipantResponse update(@PathVariable Long id, @Valid @RequestBody ParticipantRequest request) {
        return participantService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un participante", description = "Lo borra definitivamente. No se puede si está en "
            + "algún evento, en cualquier estado.")
    @ApiResponse(responseCode = "204", description = "Participante eliminado")
    @ApiResponse(responseCode = "404", description = "participant_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "participant_in_use",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void delete(@PathVariable Long id) {
        participantService.delete(id);
    }
}
