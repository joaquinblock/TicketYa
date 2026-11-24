package org.group10.ticket.mapper;

import java.util.Set;

/** Las entradas de una respuesta con una solicitud de devolución pendiente y con una rechazada. */
public record ReturnRequestMarks(Set<Long> pending, Set<Long> rejected) {

    public static final ReturnRequestMarks NONE = new ReturnRequestMarks(Set.of(), Set.of());
}
