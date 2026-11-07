package org.group10.event.statemachine;

import org.group10.event.entity.EventStatus;

public class EventStatusMachine {
    public EventStatus transition(EventStatus current, EventStatus next) {
        if (current == null || next == null) {
            throw new RuntimeException("Los estados no pueden ser null");
        }

        boolean allowed = switch (current) {
            case DRAFT -> next == EventStatus.PUBLISHED;
            case PUBLISHED ->
                    next == EventStatus.SOLD_OUT
                            || next == EventStatus.SUSPENDED
                            || next == EventStatus.CANCELLED
                            || next == EventStatus.FINISHED;
            case SOLD_OUT, SUSPENDED, CANCELLED, FINISHED -> false;
        };

        if (!allowed) {
            throw new RuntimeException(
                    "Transición no permitida: " + current + " -> " + next
            );
        }

        return next;
    }
}
