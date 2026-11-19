package org.group10.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qué tiene que cumplir un JWT para que el gateway lo acepte ({@code ticketya.jwt.*}).
 *
 * @param jwkSetUri dónde publica el auth-service su clave pública ({@code /.well-known/jwks.json})
 */
@ConfigurationProperties("ticketya.jwt")
public record GatewayJwtProperties(String issuer, String audience, String jwkSetUri) {
}
