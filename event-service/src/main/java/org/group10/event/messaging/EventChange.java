package org.group10.event.messaging;

import java.util.Locale;

/**
 * Lo que le pasó a un evento y que otros servicios tienen que saber: los compradores, si se cancela, se suspende o se
 * reprograma (US-NOT-02), y los fans de sus participantes, si se publica (US-NOT-03).
 */
public enum EventChange {
    CANCELLED,
    SUSPENDED,
    RESCHEDULED,
    PUBLISHED;

    /** {@code event.cancelled}, {@code event.suspended}, {@code event.rescheduled} o {@code event.published}. */
    public String routingKey() {
        return "event." + name().toLowerCase(Locale.ROOT);
    }
}
