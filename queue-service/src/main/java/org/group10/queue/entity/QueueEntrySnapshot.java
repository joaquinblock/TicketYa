package org.group10.queue.entity;

public record QueueEntrySnapshot(String entryId, String status, Integer position, Long enabledUntilEpochMillis) {
}
