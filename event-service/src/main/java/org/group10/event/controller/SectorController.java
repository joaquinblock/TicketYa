package org.group10.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.event.dto.SectorCreateRequest;
import org.group10.event.dto.SectorResponse;
import org.group10.event.dto.SectorUpdateRequest;
import org.group10.event.service.SectorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/venues/{venueId}/sectors")
@RequiredArgsConstructor
@Tag(name = "Sectores", description = "Sectores de un lugar: numerados (con asientos) o no numerados (con capacidad)")
public class SectorController {

    private final SectorService sectorService;

    @GetMapping
    @Operation(summary = "Listar los sectores de un lugar", description = "Ordenados por nombre.")
    @ApiResponse(responseCode = "200", description = "Sectores del lugar")
    @ApiResponse(responseCode = "404", description = "venue_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<SectorResponse> findAll(@PathVariable Long venueId) {
        return sectorService.findAll(venueId);
    }

    @GetMapping("/{sectorId}")
    @Operation(summary = "Ver un sector")
    @ApiResponse(responseCode = "200", description = "Sector encontrado")
    @ApiResponse(responseCode = "404", description = "venue_not_found / sector_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public SectorResponse findById(@PathVariable Long venueId, @PathVariable Long sectorId) {
        return sectorService.findById(venueId, sectorId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un sector", description = """
            Numerado: sin `capacity`; puede traer `rows` para crear los asientos en el mismo paso. \
            No numerado: con `capacity` y sin `rows`.""")
    @ApiResponse(responseCode = "201", description = "Sector creado")
    @ApiResponse(responseCode = "400", description = "invalid_sector_configuration",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "venue_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "sector_name_duplicated / seat_row_duplicated",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public SectorResponse create(@PathVariable Long venueId, @Valid @RequestBody SectorCreateRequest request) {
        return sectorService.create(venueId, request);
    }

    @PutMapping("/{sectorId}")
    @Operation(summary = "Modificar un sector", description = """
            Cambia el nombre y, en los no numerados, la capacidad. El tipo no se puede cambiar. \
            La capacidad queda fija cuando el sector se usa en un evento que ya salió de borrador.""")
    @ApiResponse(responseCode = "200", description = "Sector modificado")
    @ApiResponse(responseCode = "400", description = "invalid_sector_configuration",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "venue_not_found / sector_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "sector_name_duplicated / sector_in_use",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public SectorResponse update(@PathVariable Long venueId, @PathVariable Long sectorId,
                                 @Valid @RequestBody SectorUpdateRequest request) {
        return sectorService.update(venueId, sectorId, request);
    }

    @DeleteMapping("/{sectorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un sector", description = "Borra también sus asientos. "
            + "No se puede si algún evento, en cualquier estado, vende el sector.")
    @ApiResponse(responseCode = "204", description = "Sector eliminado")
    @ApiResponse(responseCode = "404", description = "venue_not_found / sector_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "sector_in_use",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void delete(@PathVariable Long venueId, @PathVariable Long sectorId) {
        sectorService.delete(venueId, sectorId);
    }
}
