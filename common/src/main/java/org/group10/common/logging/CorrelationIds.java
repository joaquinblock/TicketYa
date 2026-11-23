package org.group10.common.logging;

import org.slf4j.MDC;

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

    /**
     * El id del contexto actual, o uno nuevo si no hay. No escribe el MDC: un hilo de un job
     * no se queda con el id de una corrida para las siguientes.
     */
    public static String currentOrNew() {
        return validOrNew(MDC.get(MDC_KEY));
    }
}
