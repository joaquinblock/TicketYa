package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JWT de TicketYa listo para mandar en {@code Authorization: Bearer <accessToken>}.
 */
@Schema(description = "JWT de TicketYa")
public record TokenResponse(
        @Schema(example = "eyJhbGciOiJSUzI1NiJ9...") String accessToken,

        @Schema(example = "Bearer") String tokenType,

        @Schema(description = "Segundos hasta que vence", example = "1800") long expiresIn
) {}
