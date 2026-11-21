package org.group10.ticket.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventReportEventResponse(Long organizationId, List<Sector> sectors) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sector(String name, int capacity) {
    }
}
