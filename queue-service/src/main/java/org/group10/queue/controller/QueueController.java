package org.group10.queue.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.common.security.Caller;
import org.group10.common.security.Permission;
import org.group10.common.security.RequiresPermission;
import org.group10.queue.dto.QueueEntryResponse;
import org.group10.queue.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
@Tag(name = "Fila virtual")
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/{eventId}/join")
    @RequiresPermission(Permission.TICKET_PURCHASE)
    @Operation(summary = "Entrar a la fila virtual de un evento de alta demanda")
    @ApiResponse(responseCode = "201", description = "Ingreso creado o existente")
    @ApiResponse(responseCode = "401", description = "unauthorized")
    @ApiResponse(responseCode = "403", description = "forbidden: sin TICKET_PURCHASE")
    @ApiResponse(responseCode = "404", description = "event_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "event_not_high_demand, sale_not_started")
    @ApiResponse(responseCode = "503", description = "service_unavailable")
    public ResponseEntity<QueueEntryResponse> join(@PathVariable Long eventId, Caller caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.join(eventId, caller.userId()));
    }
}
