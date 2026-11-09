package org.group10.event.client.dto;

/**
 * Respuesta de {@code GET /api/organizations/{id}} del auth-service. Copia del DTO de ese servicio con los
 * campos que se usan acá (ver RFC-002).
 */
public record OrganizationResponse(
        Long id,
        String legalName,
        String publicName
) {}
