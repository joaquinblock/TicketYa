package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.SortedSet;

/**
 * Rol y permisos de un usuario activo. Lo consumen los demás servicios para autorizar cada request mientras no
 * haya JWT; tiene la misma forma que van a tener los claims del token (ver {@code permissions.md}).
 */
@Schema(description = "Rol y permisos de un usuario activo")
public record UserPermissionsResponse(
        @Schema(example = "2") Long userId,

        @Schema(example = "ORGANIZER") String role,

        @ArraySchema(schema = @Schema(example = "EVENT_MANAGE"),
                arraySchema = @Schema(description = "Nombres de los permisos de su rol, en orden alfabético"))
        SortedSet<String> permissions
) {}
