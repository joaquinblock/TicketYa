package org.group10.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.common.security.Caller;
import org.group10.notification.dto.NotificationResponse;
import org.group10.notification.service.NotificationInbox;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "La bandeja de avisos del usuario del token")
public class NotificationController {

    private final NotificationInbox inbox;

    @GetMapping
    @Operation(summary = "Ver mis notificaciones", description = "De la más nueva a la más vieja, de cualquier rol.")
    @ApiResponse(responseCode = "200", description = "Los avisos del usuario del token")
    @ApiResponse(responseCode = "401", description = "unauthorized: sin token",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public List<NotificationResponse> findMine(Caller caller) {
        return inbox.findFor(caller.userId());
    }

    @PostMapping("/read-all")
    @Operation(summary = "Marcar todas como leídas")
    @ApiResponse(responseCode = "204", description = "Todas quedaron leídas")
    @ApiResponse(responseCode = "401", description = "unauthorized: sin token",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<Void> markAllRead(Caller caller) {
        inbox.markAllRead(caller.userId());
        return ResponseEntity.noContent().build();
    }
}
