package org.group10.gateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * El gateway es el único que valida el JWT de TicketYa (ADR-003). Verifica la firma con la clave pública del
 * auth-service, que descarga de su JWKS, y además el {@code iss}, el {@code aud} y el vencimiento.
 */
@Configuration
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class GatewayJwtConfig {

    /**
     * Descarga el JWKS recién con el primer token y lo cachea. Si llega un {@code kid} que no conoce (el auth-service
     * se reinició con otra clave), lo vuelve a pedir.
     */
    @Bean
    JwtDecoder jwtDecoder(GatewayJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(properties.audience()))));
        return decoder;
    }

    @Bean
    IdentityHeadersFilter identityHeadersFilter(JwtDecoder jwtDecoder, JsonMapper jsonMapper) {
        return new IdentityHeadersFilter(jwtDecoder, jsonMapper);
    }
}
