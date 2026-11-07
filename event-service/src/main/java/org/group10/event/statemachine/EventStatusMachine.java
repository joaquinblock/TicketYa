package org.group10.event.statemachine;

import org.group10.event.entity.EventStatus;

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

public class EventStatusMachine {

    private static final Map<EventStatus, Set<EventStatus>> ALLOWED = new EnumMap<>(EventStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(PUBLISHED));
        ALLOWED.put(PUBLISHED, EnumSet.of(SOLD_OUT, SUSPENDED, CANCELLED, FINISHED));
        // Vuelve a PUBLISHED si se libera stock (devolución de una entrada)
        ALLOWED.put(SOLD_OUT, EnumSet.of(PUBLISHED, SUSPENDED, CANCELLED, FINISHED));
        // Al reanudar vuelve a PUBLISHED o a SOLD_OUT, según el stock del ticket-service
        ALLOWED.put(SUSPENDED, EnumSet.of(PUBLISHED, SOLD_OUT, CANCELLED));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(EventStatus.class));
        ALLOWED.put(FINISHED, EnumSet.noneOf(EventStatus.class));
    }

    public EventStatus transition(EventStatus current, EventStatus next) {

        if(current.equals(next)) return current;

        if (current == null || next == null) {
            throw new RuntimeException("Los estados no pueden ser null");
        }

        if (!ALLOWED.get(current).contains(next)) {
            throw new RuntimeException(
                    "Transición no permitida: " + current + " -> " + next
            );
        }

        return next;
    }
}
