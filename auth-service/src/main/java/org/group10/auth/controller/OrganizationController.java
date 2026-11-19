package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.AddOrganizerRequest;
import org.group10.auth.dto.OrganizationResponse;
import org.group10.auth.dto.OrganizerResponse;
import org.group10.auth.service.OrganizationService;
import org.group10.auth.service.OrganizerService;
import org.group10.common.dto.ErrorDto;
import org.group10.common.security.Permission;
import org.group10.common.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizaciones", description = "Empresas y asociaciones que organizan eventos")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizerService organizerService;

    @GetMapping("/{id}")
    @Operation(summary = "Ver una organización", description = "Público: nombre y tipo de una organización activa.")
    @ApiResponse(responseCode = "200", description = "Organización encontrada")
    @ApiResponse(responseCode = "404", description = "organization_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrganizationResponse findById(@PathVariable Long id) {
        return organizationService.findById(id);
    }

    @PostMapping("/{id}/organizers")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.USER_MANAGE)
    @Operation(summary = "Hacer organizador a un cliente", description = """
            Solo un administrador. El cliente pasa a organizar eventos para la organización y su rol pasa a \
            ORGANIZER, que se ve en su próximo token. Es lo que necesita alguien que entró con Google como cliente.""")
    @ApiResponse(responseCode = "201", description = "Organizador creado")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: sin token",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden: sin USER_MANAGE",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "organization_not_found / user_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "data_conflict: ya es organizador, o no es un cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrganizerResponse addOrganizer(@PathVariable Long id, @Valid @RequestBody AddOrganizerRequest request) {
        return organizerService.promote(id, request.userId());
    }

    @DeleteMapping("/{id}/organizers/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.USER_MANAGE)
    @Operation(summary = "Quitar a un organizador", description = """
            Solo un administrador. Deja de organizar para la organización y vuelve a ser cliente desde su próximo \
            token. Los eventos que creó siguen siendo de la organización.""")
    @ApiResponse(responseCode = "204", description = "Organizador quitado")
    @ApiResponse(responseCode = "401", description = "unauthorized: sin token",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden: sin USER_MANAGE",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "organizer_not_found: no organiza para esa organización",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public void removeOrganizer(@PathVariable Long id, @PathVariable Long userId) {
        organizerService.remove(id, userId);
    }
}
