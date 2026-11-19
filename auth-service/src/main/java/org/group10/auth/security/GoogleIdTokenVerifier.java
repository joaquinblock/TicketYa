package org.group10.auth.security;

import org.group10.auth.exception.AuthErrorCode;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Verifica el ID token que Google le da al front en el login: la firma (con las claves públicas de Google), que lo
 * haya emitido Google, que sea para TicketYa (el client id), que no esté vencido y que el email esté verificado.
 * Solo se usa para canjearlo por el JWT de TicketYa (RFC-001).
 */
public class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    /** Google emite sus ID tokens con cualquiera de los dos. */
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder googleDecoder;
    private final String clientId;

    /**
     * @param googleDecoder decoder contra las claves públicas de Google; {@code null} si no hay client id
     */
    public GoogleIdTokenVerifier(JwtDecoder googleDecoder, String clientId) {
        this.googleDecoder = googleDecoder;
        this.clientId = clientId;

    }

    /**
     * Los validadores del ID token de Google, además de la firma.
     */
    public static OAuth2TokenValidator<Jwt> validator(String clientId) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        issuer -> issuer != null && GOOGLE_ISSUERS.contains(issuer.toString())),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(clientId)));
    }

    /**
     * @throws TicketYaException {@code INVALID_CREDENTIALS} si el token no es válido o el email no está verificado;
     *                           {@code SERVICE_UNAVAILABLE} si falta el client id o no se pueden obtener las claves
     *                           de Google
     */
    public GoogleIdentity verify(String idToken) {
        if (googleDecoder == null || clientId == null || clientId.isBlank()) {
            throw new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "El login con Google no está configurado");
        }
        Jwt jwt = decode(idToken);
        String email = jwt.getClaimAsString("email");
        if (email == null || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw invalid();
        }
        return new GoogleIdentity(jwt.getSubject(), email.toLowerCase(Locale.ROOT),
                jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"));
    }

    private Jwt decode(String idToken) {
        try {
            return googleDecoder.decode(idToken);
        } catch (BadJwtException e) {
            throw invalid();
        } catch (JwtException e) {
            log.warn("No se pudo verificar el token de Google: {}", e.getMessage());
            throw new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "No se pudo verificar el login con Google en este momento. Intentá de nuevo en unos segundos");
        }
    }

    private static TicketYaException invalid() {
        return new TicketYaException(AuthErrorCode.INVALID_CREDENTIALS,
                "El token de Google es inválido, está vencido o su email no está verificado");
    }
}
