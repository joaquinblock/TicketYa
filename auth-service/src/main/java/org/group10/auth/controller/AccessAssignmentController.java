package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.AccessAssignmentOutcome;
import org.group10.auth.dto.AccessAssignmentResponse;
import org.group10.auth.service.AccessAssignmentService;
import org.group10.common.dto.ErrorDto;
import org.group10.common.security.Caller;
import org.group10.common.security.Permission;
import org.group10.common.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Personal de acceso", description = "Asignación del personal de acceso a los eventos")
public class AccessAssignmentController {

    private final AccessAssignmentService accessAssignmentService;

    @PutMapping("/{staffId}/access-assignments/{eventId}")
    @RequiresPermission(Permission.EVENT_MANAGE)
    @Operation(summary = "Asignar personal de acceso a un evento", description = """
            Solo un organizador, con personal y evento de su organización. Desde ese momento el personal puede \
            validar entradas de ese evento. Asignarlo otra vez no lo duplica. No se asigna a borradores.""")
    @ApiResponse(responseCode = "201", description = "Personal asignado")
    @ApiResponse(responseCode = "200", description = "Ya estaba asignado: no se duplica")
    @ApiResponse(responseCode = "400", description = "validation_error: id no positivo o no numérico",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: sin token",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden: sin EVENT_MANAGE, o personal o evento de otra organización",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "access_staff_not_found / event_not_found (inexistente o borrador)",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: el event-service no responde",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<AccessAssignmentResponse> assign(Caller caller, @PathVariable @Positive Long staffId,
                                                           @PathVariable @Positive Long eventId) {
        AccessAssignmentOutcome outcome = accessAssignmentService.assign(caller, staffId, eventId);

        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.assignment());
    }
}
