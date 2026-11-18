package org.group10.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final KeyPair KEYS = rsaKeyPair();
    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final JwtProperties PROPERTIES =
            new JwtProperties("ticketya-auth", "ticketya-api", Duration.ofMinutes(30), null);

    private final JwtTokenProvider provider =
            new JwtTokenProvider(encoderFor(KEYS), PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

    private final JwtDecoder decoder = decoderAt(NOW);

    @Test
    void issue_client_tokenCarriesSubRoleAndPermissions() {
        Jwt jwt = decoder.decode(provider.issue(client()));

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CLIENT");
        assertThat(jwt.getClaimAsStringList("permissions")).containsExactly("EVENT_SEARCH", "TICKET_BUY");
    }

    @Test
    void issue_anySubject_tokenCarriesIssuerAudienceAndExpiresAfterTtl() {
        Jwt jwt = decoder.decode(provider.issue(client()));

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("ticketya-auth");
        assertThat(jwt.getAudience()).containsExactly("ticketya-api");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void issue_organizer_tokenCarriesOrganizationId() {
        TokenSubject organizer = new TokenSubject(2L, "ORGANIZER", List.of("EVENT_MANAGE"), 1L);

        Jwt jwt = decoder.decode(provider.issue(organizer));

        assertThat(jwt.getClaims()).containsEntry("organizationId", 1L);
    }

    @Test
    void issue_client_tokenHasNoOrganizationIdClaim() {
        Jwt jwt = decoder.decode(provider.issue(client()));

        assertThat(jwt.hasClaim("organizationId")).isFalse();
    }

    @Test
    void issue_roleWithoutPermissions_tokenCarriesEmptyPermissions() {
        TokenSubject noPermissions = new TokenSubject(9L, "ACCESS_STAFF", List.of(), 1L);

        Jwt jwt = decoder.decode(provider.issue(noPermissions));

        assertThat(jwt.getClaimAsStringList("permissions")).isEmpty();
    }

    @Test
    void issue_twice_eachTokenHasItsOwnId() {
        Jwt first = decoder.decode(provider.issue(client()));
        Jwt second = decoder.decode(provider.issue(client()));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void decode_tamperedPayload_isRejected() {
        String[] parts = provider.issue(client()).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_signedWithAnotherKey_isRejected() {
        JwtTokenProvider impostor =
                new JwtTokenProvider(encoderFor(rsaKeyPair()), PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

        String token = impostor.issue(client());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_afterTtl_isRejectedAsExpired() {
        String token = provider.issue(client());
        JwtDecoder thirtyOneMinutesLater = decoderAt(NOW.plus(Duration.ofMinutes(31)));

        assertThatThrownBy(() -> thirtyOneMinutesLater.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    private static TokenSubject client() {
        return new TokenSubject(7L, "CLIENT", List.of("EVENT_SEARCH", "TICKET_BUY"), null);
    }

    private static JwtDecoder decoderAt(Instant instant) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(Clock.fixed(instant, ZoneOffset.UTC));
        decoder.setJwtValidator(timestamps);
        return decoder;
    }

    private static NimbusJwtEncoder encoderFor(KeyPair keys) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate())
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
