package org.group10.event.client.dto;

import java.util.Set;

/**
 * Respuesta de {@code GET /api/users/{id}/permissions} del auth-service. Copia del DTO de ese servicio con los
 * campos que se usan acá (ver RFC-002).
 */
public record UserPermissionsResponse(
        Long userId,
        String role,
        Set<String> permissions
) {}
