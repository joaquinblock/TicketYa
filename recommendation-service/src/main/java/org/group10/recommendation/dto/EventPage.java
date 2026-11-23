package org.group10.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Una página de {@code GET /api/events}. Solo interesan los eventos: se pide una sola página. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventPage(List<EventSummary> content) {
}
