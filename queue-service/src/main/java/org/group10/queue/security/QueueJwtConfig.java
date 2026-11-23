package org.group10.queue.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Base64;

@Configuration
public class QueueJwtConfig {

    private static final Logger log = LoggerFactory.getLogger(QueueJwtConfig.class);

    @Bean
    KeyPair queueJwtKeyPair(QueueJwtProperties properties) {
        String location = properties.privateKeyLocation();
        if (location == null || location.isBlank()) {
            log.warn("QUEUE_JWT_PRIVATE_KEY_LOCATION no está configurada: se usa una clave efímera");
            return generateRsaKeyPair();
        }
        return readPkcs8Pem(Path.of(location));
    }

    @Bean
    RSAKey queueJwtSigningKey(KeyPair queueJwtKeyPair) {
        try {
            return new RSAKey.Builder((RSAPublicKey) queueJwtKeyPair.getPublic())
                    .privateKey((RSAPrivateKey) queueJwtKeyPair.getPrivate())
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo calcular el kid de la clave RSA de queue-service", e);
        }
    }

    @Bean
    JwtEncoder queueJwtEncoder(RSAKey queueJwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(queueJwtSigningKey)));
    }

    @Bean
    JWKSet queuePublicJwkSet(RSAKey queueJwtSigningKey) {
        return new JWKSet(queueJwtSigningKey.toPublicJWK());
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo generar la clave RSA efímera de queue-service", e);
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
            throw new IllegalStateException("No se pudo leer la clave privada RSA de queue-service desde " + pem, e);
        }
    }
}
