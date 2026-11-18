package org.group10.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Firma tokens como lo hace el auth-service, con una clave RSA de test que publica el JWKS falso.
 */
final class TestTokens {

    static final RSAKey AUTH_KEY = rsaKey("auth-kid");
    static final RSAKey OTHER_KEY = rsaKey("other-kid");

    private TestTokens() {
    }

    static String jwks() {
        return new JWKSet(AUTH_KEY.toPublicJWK()).toString();
    }

    static String client() {
        return sign(AUTH_KEY, claims -> claims);
    }

    static String organizer() {
        return sign(AUTH_KEY, claims -> claims.subject("2").claim("role", "ORGANIZER")
                .claim("permissions", List.of("EVENT_MANAGE", "REPORT_VIEW")).claim("organizationId", 1L));
    }

    static String sign(RSAKey key, UnaryOperator<JwtClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("ticketya-auth")
                .audience(List.of("ticketya-api"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(30)))
                .subject("7")
                .claim("role", "CLIENT")
                .claim("permissions", List.of("EVENT_SEARCH", "TICKET_PURCHASE"));
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, customize.apply(claims).build())).getTokenValue();
    }

    private static RSAKey rsaKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
