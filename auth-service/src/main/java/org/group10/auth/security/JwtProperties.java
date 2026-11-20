package org.group10.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración del JWT que emite el auth-service ({@code ticketya.jwt.*}).
 *
 * @param privateKeyLocation ruta al {@code .pem} con la clave privada RSA; sin ella se usa una clave efímera
 */
@ConfigurationProperties("ticketya.jwt")
public record JwtProperties(String issuer, String audience, Duration ttl, String privateKeyLocation) {
}
