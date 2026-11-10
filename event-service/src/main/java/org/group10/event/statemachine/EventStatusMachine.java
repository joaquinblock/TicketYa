package org.group10.event.statemachine;

import org.group10.common.exception.TicketYaException;
import org.group10.event.entity.EventStatus;
import org.group10.event.exception.EventErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.group10.event.entity.EventStatus.CANCELLED;
import static org.group10.event.entity.EventStatus.DRAFT;
import static org.group10.event.entity.EventStatus.FINISHED;
import static org.group10.event.entity.EventStatus.PUBLISHED;
import static org.group10.event.entity.EventStatus.SOLD_OUT;
import static org.group10.event.entity.EventStatus.SUSPENDED;

/**
 * Allowed changes of an event's status (see {@code event-state-machine.md}). The only way to change it is
 * {@code Event.changeStatus}, which goes through here.
 */
@Component
public class EventStatusMachine {

    private static final Map<EventStatus, Set<EventStatus>> ALLOWED = new EnumMap<>(EventStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(PUBLISHED));
        ALLOWED.put(PUBLISHED, EnumSet.of(SOLD_OUT, SUSPENDED, CANCELLED, FINISHED));
        ALLOWED.put(SOLD_OUT, EnumSet.of(PUBLISHED, SUSPENDED, CANCELLED, FINISHED));
        ALLOWED.put(SUSPENDED, EnumSet.of(PUBLISHED, SOLD_OUT, CANCELLED));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(EventStatus.class));
        ALLOWED.put(FINISHED, EnumSet.noneOf(EventStatus.class));
    }

    /**
     * @return {@code next}, or {@code current} if both are the same (changing to the same status does nothing)
     * @throws IllegalArgumentException if either is {@code null}: it's a bug, not a request to reject
     * @throws TicketYaException        {@code INVALID_STATUS_TRANSITION} (409) if the change isn't allowed
     */
    public EventStatus transition(EventStatus current, EventStatus next) {
        if (current == null || next == null) {
            throw new IllegalArgumentException("Los estados no pueden ser null");
        }
        if (current == next) {
            return current;
        }
        if (!ALLOWED.get(current).contains(next)) {
            throw new TicketYaException(EventErrorCode.INVALID_STATUS_TRANSITION,
                    "Un evento en %s no puede pasar a %s".formatted(current, next));
        }
        return next;
    }
}
