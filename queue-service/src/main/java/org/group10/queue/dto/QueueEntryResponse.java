package org.group10.queue.dto;

import java.time.Instant;

public record QueueEntryResponse(String status, Integer position, String queueToken, Instant enabledUntil) {
}
