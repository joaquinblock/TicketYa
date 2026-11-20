package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.GoogleLoginRequest;
import org.group10.auth.dto.LoginResponse;
import org.group10.auth.service.AuthService;
import org.group10.common.dto.ErrorDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    @Operation(summary = "Entrar con Google",
            description = "Canjea el ID token de Google por el JWT de TicketYa. Login y registro son lo mismo: si la "
                    + "persona no tiene cuenta, se crea como cliente (newUser: true). Si un admin había cargado su "
                    + "email, se vincula y conserva su rol.")
    @ApiResponse(responseCode = "200", description = "JWT de TicketYa")
    @ApiResponse(responseCode = "400", description = "validation_error: falta el idToken",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "401", description = "invalid_credentials: token de Google inválido, vencido, de "
            + "otra app o con el email sin verificar",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "user_disabled: la cuenta está dada de baja",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "data_conflict: el email está vinculado a otra cuenta de Google",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "503", description = "service_unavailable: el login con Google no está configurado "
            + "o no se pudieron obtener las claves de Google",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public LoginResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.idToken());
    }
}
