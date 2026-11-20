package org.group10.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publica la clave pública con la que se verifica la firma de los JWT de TicketYa, en el formato estándar JWKS
 * (RFC 7517). La usa el api-gateway. No es secreta: con ella solo se puede verificar, no firmar.
 */
@RestController
@Tag(name = "Autenticación")
public class JwksController {

    private final JWKSet publicJwkSet;

    public JwksController(JWKSet publicJwkSet) {
        this.publicJwkSet = publicJwkSet;
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Clave pública para verificar los JWT",
            description = "JWKS con la clave RSA pública y su kid. Lo usa el api-gateway para validar cada token.")
    @ApiResponse(responseCode = "200", description = "Las claves públicas vigentes")
    public Map<String, Object> jwks() {
        return publicJwkSet.toJSONObject();
    }
}
