package org.group10.queue.dev;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo existe con el perfil dev, como el dev-login: sin el perfil la ruta no se registra y da 404. */
@RestController
@RequestMapping("/api/queue")
@Profile("dev")
@Tag(name = "Fila virtual (dev)", description = "Reiniciar una fila para volver a probarla")
public class DevQueueController {

    private final DevQueueReset devQueueReset;

    public DevQueueController(DevQueueReset devQueueReset) {
        this.devQueueReset = devQueueReset;
    }

    @PostMapping("/{eventId}/dev-reset")
    @Operation(summary = "Reiniciar la fila de un evento (solo perfil dev)",
            description = "Vacía la fila y vuelve a poner la gente de prueba: el front muestra 1 en el evento 1 y 3 en el 47.")
    @ApiResponse(responseCode = "204", description = "La fila quedó como recién abierta")
    public ResponseEntity<Void> reset(@PathVariable Long eventId) {
        devQueueReset.reset(eventId);
        return ResponseEntity.noContent().build();
    }
}
