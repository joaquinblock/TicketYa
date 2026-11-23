package org.group10.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Lo que se usa de {@code GET /api/events/{id}}: los eventos de la co-compra son ids y la búsqueda no filtra por id.
 * A diferencia del resumen, trae los ids de los participantes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDetail(
        Long id,
        String name,
        String type,
        Long organizationId,
        EventSummary.Venue venue,
        LocalDateTime dateTime,
        String status,
        boolean highDemand,
        List<Sector> sectors,
        List<Participant> participants
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sector(BigDecimal price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(Long id, String name) {
    }

    public List<Long> participantIds() {
        return participants == null ? List.of() : participants.stream().map(Participant::id).toList();
    }

    /** El mismo resumen que da la búsqueda: el precio desde es el del sector más barato. */
    public EventSummary toSummary() {
        BigDecimal priceFrom = sectors == null ? null : sectors.stream()
                .map(Sector::price)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        List<String> names = participants == null ? List.of() : participants.stream()
                .map(Participant::name)
                .sorted()
                .toList();
        return new EventSummary(id, name, type, status, dateTime, highDemand, organizationId, venue, priceFrom, names);
    }
}
