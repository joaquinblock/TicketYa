package org.group10.auth.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyConfigTest {

    private final JwtKeyConfig config = new JwtKeyConfig();

    @Test
    void keyPair_withoutLocation_generatesAnEphemeralRsaKey() {
        KeyPair keys = config.jwtKeyPair(propertiesWithKeyAt(null));

        assertThat(keys.getPublic()).isInstanceOf(RSAPublicKey.class);
        assertThat(((RSAPublicKey) keys.getPublic()).getModulus().bitLength()).isEqualTo(2048);
    }

    @Test
    void keyPair_withPemLocation_loadsThatKey(@TempDir Path dir) throws IOException {
        KeyPair expected = rsaKeyPair();
        Path pem = writePkcs8Pem(dir, expected);

        KeyPair keys = config.jwtKeyPair(propertiesWithKeyAt(pem.toString()));

        assertThat(keys.getPublic()).isEqualTo(expected.getPublic());
        assertThat(keys.getPrivate().getEncoded()).isEqualTo(expected.getPrivate().getEncoded());
    }

    @Test
    void keyPair_missingFile_failsNamingThePath(@TempDir Path dir) {
        String missing = dir.resolve("missing.pem").toString();

        assertThatThrownBy(() -> config.jwtKeyPair(propertiesWithKeyAt(missing)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(missing);
    }

    @Test
    void keyPair_fileIsNotAPrivateKey_fails(@TempDir Path dir) throws IOException {
        Path notAKey = Files.writeString(dir.resolve("key.pem"),
                "-----BEGIN PRIVATE KEY-----\nbm90IGEga2V5\n-----END PRIVATE KEY-----\n");

        assertThatThrownBy(() -> config.jwtKeyPair(propertiesWithKeyAt(notAKey.toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(notAKey.toString());
    }

    @Test
    void context_withJwtProperties_providesATokenProviderSignedWithTheKeyPair() {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtKeyConfig.class)
                .withPropertyValues(
                        "ticketya.jwt.issuer=ticketya-auth",
                        "ticketya.jwt.audience=ticketya-api",
                        "ticketya.jwt.ttl=30m")
                .run(context -> {
                    String token = context.getBean(JwtTokenProvider.class)
                            .issue(new TokenSubject(7L, "CLIENT", List.of(), null));
                    RSAPublicKey publicKey = (RSAPublicKey) context.getBean(KeyPair.class).getPublic();

                    Jwt jwt = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(token);

                    assertThat(jwt.getSubject()).isEqualTo("7");
                    assertThat(jwt.getClaimAsString("iss")).isEqualTo("ticketya-auth");
                });
    }

    private static JwtProperties propertiesWithKeyAt(String location) {
        return new JwtProperties("ticketya-auth", "ticketya-api", Duration.ofMinutes(30), location);
    }

    private static Path writePkcs8Pem(Path dir, KeyPair keys) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded());
        return Files.writeString(dir.resolve("private.pem"),
                "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
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
