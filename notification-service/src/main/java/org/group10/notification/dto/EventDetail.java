package org.group10.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * El evento que devuelve {@code GET /api/events/{id}} del event-service, con solo lo que usa el aviso de un cambio.
 * Ignora los demás campos para no romperse si el event-service agrega alguno.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDetail(Long id, String name, LocalDateTime dateTime, Venue venue) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Venue(String name, String address, String city) {
    }
}
