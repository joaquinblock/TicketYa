package org.group10.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Arma el verificador del ID token de Google. Sin {@code GOOGLE_CLIENT_ID} el servicio arranca igual: el login con
 * Google responde 503 y {@code dev-login} sigue andando.
 */
@Configuration
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleLoginConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginConfig.class);

    /**
     * Las claves públicas de Google se descargan con el primer login y se cachean.
     */
    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(GoogleProperties properties) {
        String clientId = properties.clientId();
        if (clientId == null || clientId.isBlank()) {
            log.warn("ticketya.google.client-id no está configurado: el login con Google responde 503");
            return new GoogleIdTokenVerifier(null, null);
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(GoogleIdTokenVerifier.validator(clientId));
        return new GoogleIdTokenVerifier(decoder, clientId);
    }
}
