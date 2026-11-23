package org.group10.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * El evento que devuelve {@code GET /api/events/{id}} del event-service, con solo lo que usan los avisos de un cambio
 * y de una publicación. Ignora los demás campos para no romperse si el event-service agrega alguno.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDetail(Long id, String name, LocalDateTime dateTime, Venue venue, String status,
                          List<Participant> participants) {

    /** Lo que necesita el aviso de un cambio, que no mira el estado ni los participantes. */
    public EventDetail(Long id, String name, LocalDateTime dateTime, Venue venue) {
        this(id, name, dateTime, venue, null, List.of());
    }

    public List<Long> participantIds() {
        return participants == null ? List.of() : participants.stream().map(Participant::id).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Venue(String name, String address, String city) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(Long id, String name) {
    }
}
