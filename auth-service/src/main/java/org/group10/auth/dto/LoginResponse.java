package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JWT de TicketYa después de entrar con Google.
 *
 * @param newUser {@code true} si la cuenta se creó en este login: el front puede mostrar el cuestionario de
 *                preferencias
 */
@Schema(description = "JWT de TicketYa y si la cuenta se creó en este login")
public record LoginResponse(
        @Schema(example = "eyJhbGciOiJSUzI1NiJ9...") String accessToken,

        @Schema(example = "Bearer") String tokenType,

        @Schema(description = "Segundos hasta que vence", example = "1800") long expiresIn,

        @Schema(description = "La cuenta se creó en este login", example = "true") boolean newUser
) {}
