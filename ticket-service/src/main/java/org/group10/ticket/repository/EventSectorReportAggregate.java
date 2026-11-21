package org.group10.ticket.repository;

import java.math.BigDecimal;

public record EventSectorReportAggregate(
        Long eventSectorId,
        Long soldTickets,
        Long refundedTickets,
        BigDecimal revenue
) {
}
