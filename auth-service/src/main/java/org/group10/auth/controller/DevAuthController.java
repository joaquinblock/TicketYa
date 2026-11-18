package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.group10.auth.dto.DevLoginRequest;
import org.group10.auth.dto.TokenResponse;
import org.group10.auth.service.AuthService;
import org.group10.common.dto.ErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emite el JWT de cualquier usuario cargado sin pasar por Google, para probar roles, Postman y la prueba de
 * carga. Solo existe con el perfil {@code dev}: sin él, la ruta da 404 (ver ADR-001).
 */
@RestController
@RequestMapping("/api/auth")
@Profile("dev")
@Tag(name = "Autenticación")
public class DevAuthController {

    private static final Logger log = LoggerFactory.getLogger(DevAuthController.class);

    private final AuthService authService;

    public DevAuthController(AuthService authService) {
        this.authService = authService;
        log.warn("dev-login está activo: cualquiera puede obtener el token de cualquier usuario. Solo para desarrollo");
    }

    @PostMapping("/dev-login")
    @Operation(summary = "Obtener el token de un usuario sin Google (solo perfil dev)",
            description = "Emite el JWT de TicketYa del usuario indicado. Sirve para probar cada rol con los "
                    + "usuarios del seeder. No existe fuera del perfil dev.")
    @ApiResponse(responseCode = "200", description = "Token del usuario")
    @ApiResponse(responseCode = "400", description = "validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "user_not_found: no existe o está dado de baja",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public TokenResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
        return authService.issueTokenFor(request.userId());
    }
}
