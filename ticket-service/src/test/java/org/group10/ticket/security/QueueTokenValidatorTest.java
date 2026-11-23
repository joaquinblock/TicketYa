package org.group10.ticket.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.group10.common.exception.CommonErrorCode;
import org.group10.ticket.exception.TicketErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueTokenValidatorTest {

    private static final String ISSUER = "ticketya-queue";
    private static final String AUDIENCE = "ticket-service";
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final RSAKey QUEUE_KEY = generateKey("queue-key");

    private final QueueTokenValidator validator = new QueueTokenValidator(decoder(QUEUE_KEY), CLOCK);

    @Test
    void validate_matchingSignedTurn_acceptsIt() {
        Instant enabledUntil = NOW.plusSeconds(60).plusMillis(500);
        String token = token(QUEUE_KEY, "7", 12L, enabledUntil, enabledUntil, ISSUER, List.of(AUDIENCE));

        assertThatCode(() -> validator.validate(token, 7L, 12L)).doesNotThrowAnyException();
    }

    @Test
    void validate_matchingDecodedClaims_acceptsThem() {
        Instant enabledUntil = NOW.plusSeconds(60).plusMillis(500);
        Jwt decoded = Jwt.withTokenValue("queue-token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .issuedAt(NOW.minusSeconds(1))
                .expiresAt(enabledUntil)
                .subject("7")
                .claim("eventId", 12L)
                .claim("enabledUntil", enabledUntil.getEpochSecond())
                .claim("jti", "queue-entry")
                .build();
        QueueTokenValidator decodedClaimsValidator = new QueueTokenValidator(token -> decoded, CLOCK);

        assertThat(decoded.getId()).isEqualTo("queue-entry");
        assertThatCode(() -> decodedClaimsValidator.validate("queue-token", 7L, 12L)).doesNotThrowAnyException();
    }

    @Test
    void validate_turnForAnotherCustomer_rejectsIt() {
        assertInvalid(token(QUEUE_KEY, "8", 12L, NOW.plusSeconds(60), NOW.plusSeconds(60), ISSUER,
                List.of(AUDIENCE)), 7L, 12L);
    }

    @Test
    void validate_turnForAnotherEvent_rejectsIt() {
        assertInvalid(token(QUEUE_KEY, "7", 13L, NOW.plusSeconds(60), NOW.plusSeconds(60), ISSUER,
                List.of(AUDIENCE)), 7L, 12L);
    }

    @Test
    void validate_expiredTurn_rejectsIt() {
        assertInvalid(token(QUEUE_KEY, "7", 12L, NOW.minusSeconds(1), NOW.minusSeconds(1), ISSUER,
                List.of(AUDIENCE)), 7L, 12L);
    }

    @Test
    void validate_enabledUntilDifferentFromExpiration_rejectsIt() {
        assertInvalid(token(QUEUE_KEY, "7", 12L, NOW.plusSeconds(60), NOW.plusSeconds(120), ISSUER,
                List.of(AUDIENCE)), 7L, 12L);
    }

    @Test
    void validate_missingQueueClaims_rejectsIt() {
        Instant enabledUntil = NOW.plusSeconds(60);
        Jwt decoded = Jwt.withTokenValue("queue-token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .expiresAt(enabledUntil)
                .subject("7")
                .claim("eventId", 12L)
                .claim("enabledUntil", enabledUntil.getEpochSecond())
                .build();
        QueueTokenValidator decodedClaimsValidator = new QueueTokenValidator(token -> decoded, CLOCK);

        assertThatThrownBy(() -> decodedClaimsValidator.validate("queue-token", 7L, 12L))
                .extracting("errorCode").isEqualTo(TicketErrorCode.INVALID_QUEUE_TOKEN);
    }

    @Test
    void validate_wrongIssuerOrAudience_rejectsIt() {
        assertInvalid(token(QUEUE_KEY, "7", 12L, NOW.plusSeconds(60), NOW.plusSeconds(60), "other-issuer",
                List.of(AUDIENCE)), 7L, 12L);
        assertInvalid(token(QUEUE_KEY, "7", 12L, NOW.plusSeconds(60), NOW.plusSeconds(60), ISSUER,
                List.of("other-service")), 7L, 12L);
    }

    @Test
    void validate_badSignature_rejectsIt() {
        String token = token(generateKey("attacker-key"), "7", 12L, NOW.plusSeconds(60), NOW.plusSeconds(60),
                ISSUER, List.of(AUDIENCE));

        assertInvalid(token, 7L, 12L);
    }

    @Test
    void validate_blankToken_rejectsItWithoutDecoding() {
        assertInvalid(null, 7L, 12L);
        assertInvalid("   ", 7L, 12L);
    }

    @Test
    void validate_jwksUnavailable_failsClosedAsServiceUnavailable() {
        QueueTokenValidator unavailable = new QueueTokenValidator(token -> {
            throw new JwtException("JWKS unavailable");
        }, CLOCK);

        assertThatThrownBy(() -> unavailable.validate("signed-token", 7L, 12L))
                .extracting("errorCode").isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    private void assertInvalid(String token, Long customer, Long event) {
        assertThatThrownBy(() -> validator.validate(token, customer, event))
                .extracting("errorCode").isEqualTo(TicketErrorCode.INVALID_QUEUE_TOKEN);
    }

    private static JwtDecoder decoder(RSAKey key) {
        NimbusJwtDecoder decoder;
        try {
            decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(CLOCK);
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> claims =
                new DelegatingOAuth2TokenValidator<>(
                        timestamps,
                        new JwtClaimValidator<String>(JwtClaimNames.ISS, ISSUER::equals),
                        new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                                audiences -> audiences != null && audiences.contains(AUDIENCE)));
        decoder.setJwtValidator(claims);
        return decoder;
    }

    private static String token(RSAKey key, String subject, Long eventId, Instant expiresAt, Instant enabledUntil,
                                String issuer, List<String> audience) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(audience)
                .issuedAt(NOW.minusSeconds(30))
                .expiresAt(expiresAt)
                .subject(subject)
                .claim("eventId", eventId)
                .claim("enabledUntil", enabledUntil.getEpochSecond())
                .claim("jti", "queue-entry")
                .build();
        try {
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)))
                    .encode(JwtEncoderParameters.from(
                            JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build(), claims))
                    .getTokenValue();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
