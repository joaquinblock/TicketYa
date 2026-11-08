package org.group10.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.event.dto.SeatBatchRequest;
import org.group10.event.dto.SeatResponse;
import org.group10.event.service.SectorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/venues/{venueId}/sectors/{sectorId}/seats")
@RequiredArgsConstructor
@Tag(name = "Asientos", description = "Asientos (fila y número) de un sector numerado")
public class SeatController {

    private final SectorService sectorService;

    @GetMapping
    @Operation(summary = "Listar los asientos de un sector", description = "Ordenados por fila y número.")
    @ApiResponse(responseCode = "200", description = "Asientos del sector (vacío si no es numerado)")
    @ApiResponse(responseCode = "404", description = "venue_not_found / sector_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<SeatResponse> findAll(@PathVariable Long venueId, @PathVariable Long sectorId) {
        return sectorService.findSeats(venueId, sectorId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar filas de asientos", description = """
            Cada fila se crea con asientos numerados del 1 a `seats`. Las filas se guardan en mayúsculas \
            y no pueden existir ya en el sector.""")
    @ApiResponse(responseCode = "201", description = "Asientos creados")
    @ApiResponse(responseCode = "404", description = "venue_not_found / sector_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "sector_not_numbered / sector_in_use / seat_row_duplicated",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<SeatResponse> addRows(@PathVariable Long venueId, @PathVariable Long sectorId,
                                      @Valid @RequestBody SeatBatchRequest request) {
        return sectorService.addSeatRows(venueId, sectorId, request);
    }

    @DeleteMapping("/{seatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un asiento", description = "No se puede si el sector se usa en un evento "
            + "que ya salió de borrador.")
    @ApiResponse(responseCode = "204", description = "Asiento eliminado")
    @ApiResponse(responseCode = "404", description = "venue_not_found / sector_not_found / seat_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "sector_in_use",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void delete(@PathVariable Long venueId, @PathVariable Long sectorId, @PathVariable Long seatId) {
        sectorService.deleteSeat(venueId, sectorId, seatId);
    }
}
