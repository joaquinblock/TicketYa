package org.group10.common.logging;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationIds {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private CorrelationIds() {
    }

    public static String validOrNew(String candidate) {
        return candidate != null && VALID_ID.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }
}
