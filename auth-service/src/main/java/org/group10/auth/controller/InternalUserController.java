package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.UserContactResponse;
import org.group10.auth.service.UserService;
import org.group10.common.dto.ErrorDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios (interno)", description = "Datos de usuarios, solo entre servicios")
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Ver el contacto de un usuario",
            description = "Lo usa el notification-service para enviar el mail de una compra. Un usuario dado de baja "
                    + "da 404.")
    @ApiResponse(responseCode = "200", description = "Usuario activo con su email y su nombre")
    @ApiResponse(responseCode = "400", description = "validation_error: id no positivo",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "unauthorized: falta el secreto interno o es incorrecto",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "user_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public UserContactResponse findContact(@PathVariable @Positive Long id) {
        return userService.findContact(id);
    }
}
