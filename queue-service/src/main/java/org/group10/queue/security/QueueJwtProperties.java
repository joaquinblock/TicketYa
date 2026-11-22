package org.group10.queue.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ticketya.queue.jwt")
public record QueueJwtProperties(String issuer, String audience, String privateKeyLocation) {
}
