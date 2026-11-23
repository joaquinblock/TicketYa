package org.group10.event.messaging;

import java.util.Locale;

/**
 * Lo que le pasó a un evento y que los compradores tienen que saber (US-NOT-02).
 */
public enum EventChange {
    CANCELLED,
    SUSPENDED,
    RESCHEDULED;

    /** {@code event.cancelled}, {@code event.suspended} o {@code event.rescheduled}. */
    public String routingKey() {
        return "event." + name().toLowerCase(Locale.ROOT);
    }
}
