package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.OrganizationResponse;
import org.group10.auth.service.OrganizationService;
import org.group10.common.dto.ErrorDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizaciones", description = "Empresas y asociaciones que organizan eventos")
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/{id}")
    @Operation(summary = "Ver una organización",
            description = "Lo usa el event-service para validar la organización al crear un evento.")
    @ApiResponse(responseCode = "200", description = "Organización encontrada")
    @ApiResponse(responseCode = "404", description = "organization_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrganizationResponse findById(@PathVariable Long id) {
        return organizationService.findById(id);
    }
}
