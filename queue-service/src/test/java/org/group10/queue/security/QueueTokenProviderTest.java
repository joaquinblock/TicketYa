package org.group10.queue.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueueTokenProviderTest {

    private static final KeyPair KEYS = rsaKeyPair();
    private static final Instant ENABLED_AT = Instant.parse("2030-01-01T10:00:00Z");
    private static final Instant ENABLED_UNTIL = ENABLED_AT.plus(Duration.ofMinutes(10));
    private static final QueueJwtProperties PROPERTIES =
            new QueueJwtProperties("ticketya-queue", "ticket-service", null);

    private final QueueTokenProvider provider = new QueueTokenProvider(encoderFor(KEYS), PROPERTIES);

    @Test
    void issue_turnTokenCarriesUserEventAndFixedExpiry() {
        Jwt jwt = decoderAt(ENABLED_AT).decode(
                provider.issue(10L, 7L, "7:1", ENABLED_AT, ENABLED_UNTIL));

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaim("eventId")).isEqualTo(10L);
        assertThat(jwt.getClaim("enabledUntil")).isEqualTo(ENABLED_UNTIL.getEpochSecond());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("ticketya-queue");
        assertThat(jwt.getAudience()).containsExactly("ticket-service");
        assertThat(jwt.getIssuedAt()).isEqualTo(ENABLED_AT);
        assertThat(jwt.getExpiresAt()).isEqualTo(ENABLED_UNTIL);
        assertThat(jwt.getId()).isEqualTo("7:1");
    }

    @Test
    void issue_turnTokenUsesTheQueueSigningKey() {
        Jwt jwt = decoderAt(ENABLED_AT).decode(
                provider.issue(10L, 7L, "7:1", ENABLED_AT, ENABLED_UNTIL));

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getHeaders().get("kid")).isNotBlank();
    }

    private static NimbusJwtDecoder decoderAt(Instant instant) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(Clock.fixed(instant, ZoneOffset.UTC));
        decoder.setJwtValidator(timestamps);
        return decoder;
    }

    private static NimbusJwtEncoder encoderFor(KeyPair keys) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate())
                .keyIDFromThumbprint()
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
