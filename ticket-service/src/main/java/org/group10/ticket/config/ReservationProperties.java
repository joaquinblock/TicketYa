package org.group10.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("ticketya.reservation")
public record ReservationProperties(Duration hold) {
}
