package org.group10.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.common.security.Caller;
import org.group10.common.security.Permission;
import org.group10.common.security.RequiresPermission;
import org.group10.ticket.dto.EventReportResponse;
import org.group10.ticket.service.EventReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Resultados de venta de eventos")
public class EventReportController {

    private final EventReportService eventReportService;

    @GetMapping("/{eventId}/report")
    @RequiresPermission(Permission.REPORT_VIEW)
    @Operation(summary = "Consultar el reporte de un evento",
            description = "Disponible para organizadores con REPORT_VIEW y solo para eventos de su organización.")
    @ApiResponse(responseCode = "200", description = "Reporte de ventas por sector y total del evento")
    @ApiResponse(responseCode = "400", description = "validation_error: eventId debe ser positivo",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: sin identidad",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden: sin REPORT_VIEW o evento de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "event_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: event-service no responde",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public EventReportResponse findReport(@PathVariable @Positive Long eventId, Caller caller) {
        return eventReportService.findReport(caller, eventId);
    }
}
