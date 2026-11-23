package org.group10.queue.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QueueEventResponse(Long id, boolean highDemand, LocalDateTime saleStart) {
}
