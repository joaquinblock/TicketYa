package org.group10.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ticketya.queue")
public record QueueProperties(int batchSize, Duration batchInterval, Duration enabledWindow) {

    public QueueProperties {
        if (batchSize < 1 || batchInterval == null || batchInterval.isZero() || batchInterval.isNegative()
                || enabledWindow == null || enabledWindow.isZero() || enabledWindow.isNegative()) {
            throw new IllegalArgumentException("La configuración de la fila debe usar valores positivos");
        }
    }
}
