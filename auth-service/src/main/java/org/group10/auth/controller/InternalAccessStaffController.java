package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.auth.service.AccessAssignmentService;
import org.group10.common.dto.ErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/access-staff")
@RequiredArgsConstructor
@Tag(name = "Personal de acceso (interno)", description = "Asignaciones del personal de acceso, solo entre servicios")
public class InternalAccessStaffController {

    private final AccessAssignmentService accessAssignmentService;

    @GetMapping("/{staffId}/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirmar que el personal está asignado al evento",
            description = "Lo usa el ticket-service antes de validar un QR. El personal dado de baja, o un usuario "
                    + "que no es personal de acceso, da 403.")
    @ApiResponse(responseCode = "204", description = "Personal activo asignado al evento")
    @ApiResponse(responseCode = "400", description = "validation_error: id no positivo",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: falta el secreto interno o es incorrecto",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "staff_not_assigned",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void ensureAssigned(@PathVariable @Positive Long staffId, @PathVariable @Positive Long eventId) {
        accessAssignmentService.ensureAssigned(staffId, eventId);
    }
}
