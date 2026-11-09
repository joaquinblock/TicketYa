package org.group10.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.group10.auth.entity.OrganizationType;

/**
 * Datos públicos de una organización. No incluye el CUIT ni la cuenta bancaria: no tienen por qué
 * viajar a otros servicios.
 */
@Schema(description = "Organización que organiza eventos")
public record OrganizationResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Producciones del Sur S.A.") String legalName,
        @Schema(description = "Nombre que se muestra en las páginas de eventos", example = "Del Sur Producciones")
        String displayName,
        @Schema(example = "COMPANY") OrganizationType type
) {}
