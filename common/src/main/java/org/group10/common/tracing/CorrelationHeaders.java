package org.group10.common.tracing;

import java.util.UUID;

public final class CorrelationHeaders {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private CorrelationHeaders() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = value.trim();
        return normalized.length() <= 128 ? normalized : UUID.randomUUID().toString();
    }

    public static String currentOrCreate() {
        String current = org.slf4j.MDC.get(MDC_KEY);
        if (current == null || current.isBlank()) {
            current = UUID.randomUUID().toString();
            org.slf4j.MDC.put(MDC_KEY, current);
        }
        return current;
    }
}
