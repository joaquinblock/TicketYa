package org.group10.queue.controller;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Fila virtual")
public class QueueJwksController {

    private final JWKSet publicJwkSet;

    public QueueJwksController(JWKSet publicJwkSet) {
        this.publicJwkSet = publicJwkSet;
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Clave pública para verificar turnos de compra")
    @ApiResponse(responseCode = "200", description = "JWKS con la clave pública vigente")
    public Map<String, Object> jwks() {
        return publicJwkSet.toJSONObject();
    }
}
