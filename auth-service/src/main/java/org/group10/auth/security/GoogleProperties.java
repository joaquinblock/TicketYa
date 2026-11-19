package org.group10.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del login con Google ({@code ticketya.google.*}).
 *
 * @param clientId  client id de OAuth del proyecto de Google Cloud; vacío = login con Google desactivado
 * @param jwkSetUri dónde publica Google sus claves públicas
 */
@ConfigurationProperties("ticketya.google")
public record GoogleProperties(
        String clientId,
        @DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String jwkSetUri) {
}
