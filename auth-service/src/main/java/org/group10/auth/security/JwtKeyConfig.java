package org.group10.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;

/**
 * Clave RSA con la que el auth-service firma sus JWT.
 *
 * <p>Con {@code ticketya.jwt.private-key-location} lee un {@code .pem} PKCS#8, que nunca va en el repo.
 * Sin esa ruta genera una clave efímera: sirve para levantar el servicio en desarrollo, pero los tokens
 * dejan de valer al reiniciar.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    KeyPair jwtKeyPair(JwtProperties properties) {
        String location = properties.privateKeyLocation();
        if (location == null || location.isBlank()) {
            log.warn("ticketya.jwt.private-key-location no está configurada: se usa una clave efímera");
            return generateRsaKeyPair();
        }
        return readPkcs8Pem(Path.of(location));
    }

    /**
     * La clave con su {@code kid}: el thumbprint de la pública (RFC 7638). Cambia si cambia la clave, así quien
     * valida sabe cuándo tiene que volver a pedir el JWKS.
     */
    @Bean
    RSAKey jwtSigningKey(KeyPair jwtKeyPair) {
        try {
            return new RSAKey.Builder((RSAPublicKey) jwtKeyPair.getPublic())
                    .privateKey((RSAPrivateKey) jwtKeyPair.getPrivate())
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo calcular el kid de la clave RSA", e);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /**
     * Solo la parte pública: es lo que publica {@code /.well-known/jwks.json} para que el gateway verifique la firma.
     */
    @Bean
    JWKSet publicJwkSet(RSAKey jwtSigningKey) {
        return new JWKSet(jwtSigningKey.toPublicJWK());
    }

    @Bean
    JwtTokenProvider jwtTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties) {
        return new JwtTokenProvider(jwtEncoder, properties, Clock.systemUTC());
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo generar la clave RSA efímera", e);
        }
    }

    private static KeyPair readPkcs8Pem(Path pem) {
        try {
            String base64 = Files.readString(pem)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) rsa.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPublicKey publicKey = (RSAPublicKey) rsa.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new KeyPair(publicKey, privateKey);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("No se pudo leer la clave privada RSA de " + pem, e);
        }
    }
}
