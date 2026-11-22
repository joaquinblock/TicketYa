package org.group10.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.group10.common.dto.ErrorDto;
import org.group10.common.security.Caller;
import org.group10.recommendation.dto.PreferencesRequest;
import org.group10.recommendation.dto.PreferencesResponse;
import org.group10.recommendation.service.PreferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences/me")
@RequiredArgsConstructor
@Tag(name = "Preferencias", description = "Tipos de evento, artistas favoritos y ciudad del cliente")
public class PreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping
    @Operation(summary = "Ver mis preferencias", description = "Solo clientes. Sin preferencias guardadas, vienen vacías.")
    @ApiResponse(responseCode = "200", description = "Preferencias del cliente")
    @ApiResponse(responseCode = "403", description = "forbidden: el usuario no es cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public PreferencesResponse get(Caller caller) {
        return preferenceService.get(caller);
    }

    @PutMapping
    @Operation(summary = "Reemplazar mis preferencias",
            description = "Solo clientes. Reemplaza todas: lo que no viene queda vacío. Los repetidos se guardan una vez.")
    @ApiResponse(responseCode = "200", description = "Preferencias guardadas")
    @ApiResponse(responseCode = "403", description = "forbidden: el usuario no es cliente",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "400", description = "invalid_event_type / validation_error",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "participant_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public PreferencesResponse replace(Caller caller, @Valid @RequestBody PreferencesRequest request) {
        return preferenceService.replace(caller, request);
    }
}
