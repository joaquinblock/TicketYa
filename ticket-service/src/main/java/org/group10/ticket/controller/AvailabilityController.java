package org.group10.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.ticket.dto.EventAvailabilityResponse;
import org.group10.ticket.service.AvailabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
@Tag(name = "Disponibilidad", description = "Lugares libres de un evento")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    @Operation(summary = "Consultar la disponibilidad de un evento", description = "Se lee siempre de la base, sin cache.")
    @ApiResponse(responseCode = "200", description = "Disponibilidad por sector")
    @ApiResponse(responseCode = "404", description = "resource_not_found: el evento no tiene lugares a la venta",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventAvailabilityResponse findAvailability(@RequestParam @Positive Long eventId) {
        return availabilityService.findAvailability(eventId);
    }
}
