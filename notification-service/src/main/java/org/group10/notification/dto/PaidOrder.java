package org.group10.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * La orden pagada que devuelve {@code GET /internal/orders/{id}} del ticket-service, con solo lo que usa el mail.
 * Ignora los demás campos para no romperse si el ticket-service agrega alguno.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaidOrder(Long id, Event event, BigDecimal total, List<Ticket> tickets) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String name, LocalDateTime dateTime, String venueName, String venueAddress,
                        String venueCity) {
    }

    /**
     * En un sector no numerado, {@code row} y {@code number} vienen en {@code null}. {@code qrCode} viene solo en las
     * entradas {@code ISSUED}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ticket(Long id, String sectorName, String row, Integer number, BigDecimal price, String qrCode) {
    }
}
