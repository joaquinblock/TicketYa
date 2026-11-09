package org.group10.event.client.dto;

/**
 * Respuesta de {@code GET /api/organizers/{userId}} del auth-service. Copia del DTO de ese servicio con los
 * campos que se usan acá (ver RFC-002).
 */
public record OrganizerResponse(
        Long userId,
        Long organizationId
) {}
