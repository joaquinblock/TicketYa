package org.group10.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.notification.entity.Notification;

import java.time.Instant;

@Schema(description = "Un aviso de la bandeja del usuario")
public record NotificationResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "Tu compra está confirmada") String title,
        @Schema(example = "Tus entradas ya están en Mis entradas, con su código QR.") String body,
        @Schema(example = "2026-11-23T21:05:00Z") Instant createdAt,
        @Schema(example = "false") boolean read
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getTitle(), notification.getBody(),
                notification.getCreatedAt(), notification.isRead());
    }
}
