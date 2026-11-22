package org.group10.queue.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class QueueTokenProvider {

    private final JwtEncoder encoder;
    private final QueueJwtProperties properties;

    public QueueTokenProvider(JwtEncoder queueJwtEncoder, QueueJwtProperties properties) {
        this.encoder = queueJwtEncoder;
        this.properties = properties;
    }

    public String issue(Long eventId, Long userId, String entryId, Instant enabledAt, Instant enabledUntil) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(enabledAt)
                .expiresAt(enabledUntil)
                .id(entryId)
                .subject(userId.toString())
                .claim("eventId", eventId)
                .claim("enabledUntil", enabledUntil.getEpochSecond())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
