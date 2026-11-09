package org.group10.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.group10.auth.dto.OrganizerResponse;
import org.group10.auth.service.OrganizerService;
import org.group10.common.dto.ErrorDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizers")
@RequiredArgsConstructor
@Tag(name = "Organizadores", description = "Personas que organizan eventos en nombre de una organización")
public class OrganizerController {

    private final OrganizerService organizerService;

    @GetMapping("/{userId}")
    @Operation(summary = "Ver un organizador activo",
            description = "Lo usa el event-service al crear un evento para validar al organizador y su "
                    + "organización. Un organizador dado de baja, o cuyo usuario se dio de baja, da 404.")
    @ApiResponse(responseCode = "200", description = "Organizador activo")
    @ApiResponse(responseCode = "404", description = "organizer_not_found",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public OrganizerResponse findById(@PathVariable Long userId) {
        return organizerService.findActiveByUserId(userId);
    }
}
