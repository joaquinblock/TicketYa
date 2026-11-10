package org.group10.ticket.dto;

import org.group10.ticket.entity.ReturnRequestStatus;

import java.time.LocalDateTime;

public record ReturnRequestResponse(
        Long id,
        Long ticketId,
        Long eventId,
        Long requestedBy,
        LocalDateTime requestedAt,
        ReturnRequestStatus status,
        Long resolvedBy,
        LocalDateTime resolvedAt
) {}
