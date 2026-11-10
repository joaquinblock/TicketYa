package org.group10.ticket.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.ticket.dto.ReturnRequestPageResponse;
import org.group10.ticket.dto.ReturnRequestResponse;
import org.group10.ticket.entity.ReturnRequestStatus;
import org.group10.ticket.service.ReturnRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Devoluciones", description = "Solicitudes de devolución de entradas y su resolución")
public class ReturnRequestController {

    private static final String USER_HEADER = "X-User-Id";

    private final ReturnRequestService returnRequestService;

    @PostMapping("/tickets/{ticketId}/return-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Solicitar la devolución de una entrada", description = "Hasta 72 horas antes del evento. La entrada sigue ISSUED hasta que se resuelva.")
    @ApiResponse(responseCode = "201", description = "Solicitud creada en PENDING")
    @ApiResponse(responseCode = "404", description = "ticket_not_found: no existe o es de otro cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "refund_not_allowed o return_request_already_pending",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ReturnRequestResponse requestReturn(@RequestHeader(USER_HEADER) @Positive Long userId,
                                               @PathVariable @Positive Long ticketId) {
        return returnRequestService.requestReturn(userId, ticketId);
    }

    @GetMapping("/return-requests")
    @Operation(summary = "Listar las solicitudes de devolución", description = "Paginado, primero las más antiguas. Por defecto, las PENDING.")
    @ApiResponse(responseCode = "200", description = "Página de solicitudes")
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ReturnRequestPageResponse findByStatus(@RequestParam(defaultValue = "PENDING") ReturnRequestStatus status,
                                                  @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return returnRequestService.findByStatus(status, page, size);
    }

    @PostMapping("/return-requests/{id}/approve")
    @Operation(summary = "Aprobar una devolución", description = "Pide el reintegro, marca la entrada REFUNDED y libera el lugar. Si payment-service no responde, se puede reintentar sin un segundo reintegro.")
    @ApiResponse(responseCode = "200", description = "Solicitud aprobada")
    @ApiResponse(responseCode = "403", description = "forbidden: el usuario no tiene el permiso REFUND_RESOLVE",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "return_request_not_found o user_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "refund_not_allowed o ticket_already_used",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: la solicitud sigue PENDING",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ReturnRequestResponse approve(@RequestHeader(USER_HEADER) @Positive Long userId,
                                         @PathVariable @Positive Long id) {
        return returnRequestService.approve(userId, id);
    }

    @PostMapping("/return-requests/{id}/reject")
    @Operation(summary = "Rechazar una devolución", description = "La entrada sigue vigente y no se pide ningún reintegro.")
    @ApiResponse(responseCode = "200", description = "Solicitud rechazada")
    @ApiResponse(responseCode = "403", description = "forbidden: el usuario no tiene el permiso REFUND_RESOLVE",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "return_request_not_found o user_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "refund_not_allowed",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ReturnRequestResponse reject(@RequestHeader(USER_HEADER) @Positive Long userId,
                                        @PathVariable @Positive Long id) {
        return returnRequestService.reject(userId, id);
    }
}
