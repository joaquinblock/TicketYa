package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.UserPermissionsResponse;
import org.group10.auth.service.UserService;
import org.group10.common.dto.ErrorDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Usuarios del sistema y lo que su rol les permite hacer")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}/permissions")
    @Operation(summary = "Ver el rol y los permisos de un usuario",
            description = "Lo usan los demás servicios para autorizar cada request, porque en la Entrega 1 no hay "
                    + "JWT. Un usuario dado de baja da 404.")
    @ApiResponse(responseCode = "200", description = "Usuario activo con su rol y sus permisos")
    @ApiResponse(responseCode = "404", description = "user_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public UserPermissionsResponse findPermissions(@PathVariable Long id) {
        return userService.findPermissions(id);
    }
}
