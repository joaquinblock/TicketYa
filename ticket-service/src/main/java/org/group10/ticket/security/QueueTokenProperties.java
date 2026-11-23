package org.group10.ticket.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ticketya.queue")
public record QueueTokenProperties(String jwkSetUri, String issuer, String audience) {
}
