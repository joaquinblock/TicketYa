package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Organizador activo y la organización para la que trabaja. Lo consume el event-service al crear un evento.
 */
@Schema(description = "Organizador activo con su organización")
public record OrganizerResponse(
        @Schema(description = "Id del usuario, que es también el del organizador", example = "2")
        Long userId,

        @Schema(example = "María") String firstName,
        @Schema(example = "Gómez") String lastName,

        @Schema(example = "1") Long organizationId
) {}
