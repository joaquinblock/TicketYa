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
import org.group10.common.security.Permission;
import org.group10.common.security.RequiresPermission;
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
    @RequiresPermission(Permission.USER_MANAGE)
    @Operation(summary = "Ver el rol y los permisos de un usuario",
            description = "Solo un administrador. Los servicios ya no lo usan para autorizar: los permisos viajan en "
                    + "el token. Un usuario dado de baja da 404.")
    @ApiResponse(responseCode = "200", description = "Usuario activo con su rol y sus permisos")
    @ApiResponse(responseCode = "401", description = "unauthorized: sin token",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "forbidden: sin USER_MANAGE",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "user_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public UserPermissionsResponse findPermissions(@PathVariable Long id) {
        return userService.findPermissions(id);
    }
}
