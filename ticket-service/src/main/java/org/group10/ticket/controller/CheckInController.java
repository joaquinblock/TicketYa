package org.group10.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.common.security.Caller;
import org.group10.common.security.Permission;
import org.group10.common.security.RequiresPermission;
import org.group10.ticket.dto.CheckInRequest;
import org.group10.ticket.dto.CheckInResponse;
import org.group10.ticket.service.CheckInService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Ingreso", description = "Validación del QR de las entradas en el ingreso al evento")
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/check-in")
    @RequiresPermission(Permission.TICKET_VALIDATE)
    @Operation(summary = "Validar el QR de una entrada en el ingreso",
            description = "Solo el personal de acceso. Si la entrada es del evento y está ISSUED, pasa a USED con quién la validó y cuándo. Un QR entra una sola vez, aun con escaneos simultáneos.")
    @ApiResponse(responseCode = "200", description = "Entrada validada: puede pasar")
    @ApiResponse(responseCode = "400", description = "invalid_qr: el QR no existe, es de otro evento o la entrada está anulada o reintegrada; o validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: sin token",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden: sin TICKET_VALIDATE",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "ticket_already_used: la entrada ya ingresó",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public CheckInResponse checkIn(Caller caller, @Valid @RequestBody CheckInRequest request) {
        return checkInService.checkIn(caller.userId(), request.eventId(), request.qrCode());
    }
}
