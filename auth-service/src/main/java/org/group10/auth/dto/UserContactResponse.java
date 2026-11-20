package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos de contacto de un usuario activo. Lo usa el notification-service para enviarle el mail de una compra.
 */
@Schema(description = "Datos de contacto de un usuario activo")
public record UserContactResponse(
        @Schema(example = "1") Long userId,

        @Schema(example = "client@ticketya.com") String email,

        @Schema(example = "Juan") String firstName,

        @Schema(example = "Pérez") String lastName
) {}
