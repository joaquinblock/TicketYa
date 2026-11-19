package org.group10.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "ticketya-test.apps.googleusercontent.com";
    private static final RSAKey GOOGLE_KEY = rsaKey("google-kid");
    private static final RSAKey OTHER_KEY = rsaKey("other-kid");

    private final GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(googleDecoder(), CLIENT_ID);

    @Test
    void verify_validToken_returnsTheGoogleIdentityWithTheEmailInLowerCase() {
        GoogleIdentity identity = verifier.verify(googleToken(GOOGLE_KEY, claims -> claims
                .claim("email", "Ana.Gomez@Gmail.com")));

        assertThat(identity).isEqualTo(new GoogleIdentity("google-sub-123", "ana.gomez@gmail.com", "Ana", "Gómez"));
    }

    @Test
    void verify_issuerWithoutScheme_isAlsoValid() {
        GoogleIdentity identity = verifier.verify(googleToken(GOOGLE_KEY, claims -> claims
                .issuer("accounts.google.com")));

        assertThat(identity.subject()).isEqualTo("google-sub-123");
    }

    @Test
    void verify_tokenForAnotherApp_isRejected() {
        assertInvalid(googleToken(GOOGLE_KEY, claims -> claims.audience(List.of("other-app.apps.googleusercontent.com"))));
    }

    @Test
    void verify_tokenNotIssuedByGoogle_isRejected() {
        assertInvalid(googleToken(GOOGLE_KEY, claims -> claims.issuer("https://evil.example.com")));
    }

    @Test
    void verify_expiredToken_isRejected() {
        assertInvalid(googleToken(GOOGLE_KEY, claims -> claims
                .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600))));
    }

    @Test
    void verify_tokenSignedWithAnotherKey_isRejected() {
        assertInvalid(googleToken(OTHER_KEY, claims -> claims));
    }

    @Test
    void verify_notAJwt_isRejected() {
        assertInvalid("not-a-jwt");
    }

    @Test
    void verify_emailNotVerified_isRejected() {
        assertInvalid(googleToken(GOOGLE_KEY, claims -> claims.claim("email_verified", false)));
    }

    @Test
    void verify_withoutEmail_isRejected() {
        assertInvalid(googleToken(GOOGLE_KEY, claims -> claims.claims(c -> c.remove("email"))));
    }

    @Test
    void verify_googleKeysUnreachable_throwsServiceUnavailable() {
        JwtDecoder unreachable = token -> {
            throw new JwtException("Couldn't retrieve remote JWK set");
        };

        assertThatThrownBy(() -> new GoogleIdTokenVerifier(unreachable, CLIENT_ID).verify("any"))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void verify_withoutClientId_throwsServiceUnavailable() {
        assertThatThrownBy(() -> new GoogleIdTokenVerifier(null, "").verify(googleToken(GOOGLE_KEY, claims -> claims)))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }

    private static JwtDecoder googleDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(toPublicKey(GOOGLE_KEY)).build();
        decoder.setJwtValidator(GoogleIdTokenVerifier.validator(CLIENT_ID));
        return decoder;
    }

    private static String googleToken(RSAKey key, UnaryOperator<JwtClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .subject("google-sub-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("email", "ana.gomez@gmail.com")
                .claim("email_verified", true)
                .claim("given_name", "Ana")
                .claim("family_name", "Gómez");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, customize.apply(claims).build())).getTokenValue();
    }

    private static java.security.interfaces.RSAPublicKey toPublicKey(RSAKey key) {
        try {
            return key.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey rsaKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
