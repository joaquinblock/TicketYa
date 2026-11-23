package org.group10.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.dto.ErrorDto;
import org.group10.payment.dto.MockGatewayBehaviorRequest;
import org.group10.payment.gateway.MockGatewayBehavior;
import org.group10.payment.gateway.MockPaymentGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cambia en ejecución la demora y la caída de la pasarela simulada, para probar la resiliencia
 * desde Postman sin reiniciar. Solo existe con el perfil {@code dev}: sin él, la ruta da 404 (ver ADR-001).
 */
@RestController
@RequestMapping("/internal/mock/gateway")
@Profile("dev")
@Slf4j
@Tag(name = "Pasarela simulada", description = "Solo perfil dev: simular lentitud y caída de la pasarela")
public class DevMockGatewayController {

    private final MockPaymentGateway mockPaymentGateway;

    public DevMockGatewayController(MockPaymentGateway mockPaymentGateway) {
        this.mockPaymentGateway = mockPaymentGateway;
    }

    @GetMapping
    @Operation(summary = "Ver cómo responde la pasarela simulada (solo perfil dev)")
    @ApiResponse(responseCode = "200", description = "Demora en milisegundos y si está caída")
    public MockGatewayBehavior behavior() {
        return mockPaymentGateway.behavior();
    }

    @PutMapping
    @Operation(summary = "Cambiar la demora y la caída de la pasarela simulada (solo perfil dev)",
            description = "Afecta a los cobros y a los reintegros. Con down en true, cada llamada falla; "
                    + "con una demora mayor al timeout, cada intento se corta.")
    @ApiResponse(responseCode = "200", description = "Comportamiento nuevo")
    @ApiResponse(responseCode = "400", description = "validation_error: delayMs entre 0 y 60000, down obligatorio",
            content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    public MockGatewayBehavior changeBehavior(@Valid @RequestBody MockGatewayBehaviorRequest request) {
        MockGatewayBehavior newBehavior = request.toBehavior();
        mockPaymentGateway.changeBehavior(newBehavior);
        log.warn("Pasarela simulada: demora {} ms, caída {}", newBehavior.delayMs(), newBehavior.down());
        return mockPaymentGateway.behavior();
    }
}
