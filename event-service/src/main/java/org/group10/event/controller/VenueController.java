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
import org.group10.event.dto.VenueDetailResponse;
import org.group10.event.dto.VenueRequest;
import org.group10.event.dto.VenueSummaryResponse;
import org.group10.event.service.VenueService;
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

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@Tag(name = "Lugares", description = "ABM de lugares (estadios, teatros, salas)")
public class VenueController {

    private final VenueService venueService;

    @GetMapping
    @Operation(summary = "Listar lugares", description = "Ordenados por nombre. Se pueden filtrar por ciudad.")
    @ApiResponse(responseCode = "200", description = "Lugares encontrados (puede ser una lista vacía)")
    public List<VenueSummaryResponse> findAll(
            @Parameter(description = "Ciudad, sin distinguir mayúsculas", example = "Tandil")
            @RequestParam(required = false) String city) {
        return venueService.findAll(city);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un lugar", description = "Incluye sus sectores y la capacidad total calculada.")
    @ApiResponse(responseCode = "200", description = "Lugar encontrado")
    @ApiResponse(responseCode = "404", description = "venue_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public VenueDetailResponse findById(@PathVariable Long id) {
        return venueService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un lugar", description = "Se crea sin sectores; se agregan con /venues/{id}/sectors.")
    @ApiResponse(responseCode = "201", description = "Lugar creado")
    public VenueSummaryResponse create(@Valid @RequestBody VenueRequest request) {
        return venueService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar un lugar", description = "Reemplaza nombre, dirección, ciudad y coordenadas.")
    @ApiResponse(responseCode = "200", description = "Lugar modificado")
    @ApiResponse(responseCode = "404", description = "venue_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public VenueSummaryResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest request) {
        return venueService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un lugar", description = "Borra también sus sectores y asientos. "
            + "No se puede si el lugar tiene eventos, en cualquier estado.")
    @ApiResponse(responseCode = "204", description = "Lugar eliminado")
    @ApiResponse(responseCode = "404", description = "venue_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "venue_in_use",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void delete(@PathVariable Long id) {
        venueService.delete(id);
    }
}
