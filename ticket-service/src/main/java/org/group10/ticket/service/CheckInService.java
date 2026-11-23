package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.logging.CorrelationIds;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.CheckInResponse;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Ingreso al evento (US-TKT-19): el personal escanea el QR y la entrada entra una sola vez.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInService {

    private final TicketRepository ticketRepository;
    private final Clock clock;

    @Transactional
    public CheckInResponse checkIn(Long staffId, Long eventId, String qrCode) {
        Ticket ticket = ticketRepository.findWithOrderByQrCode(qrCode)
                .filter(found -> found.getOrder().getEventId().equals(eventId))
                .orElseThrow(() -> invalidQr(eventId));

        if (ticket.getStatus() == TicketStatus.USED) {
            throw alreadyUsed(ticket);
        }
        if (ticket.getStatus() != TicketStatus.ISSUED) {
            throw invalidQr(eventId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (ticketRepository.markIssuedTicketAsUsed(ticket.getId(), now, staffId) == 0) {
            throw alreadyUsed(ticket);
        }
        log.info("operation=check_in status=success ticketId={} eventId={} userId={} correlationId={}",
                ticket.getId(), eventId, staffId, CorrelationIds.currentOrNew());
        return new CheckInResponse(ticket.getId(), ticket.getSectorName(), ticket.getRow(), ticket.getNumber(), now);
    }

    /**
     * Mismo error si el QR no existe, es de otro evento o la entrada no puede entrar: no revela a qué evento pertenece
     * un QR ajeno.
     */
    private static TicketYaException invalidQr(Long eventId) {
        return new TicketYaException(TicketErrorCode.INVALID_QR,
                "El QR no corresponde a una entrada válida del evento %d".formatted(eventId));
    }

    private static TicketYaException alreadyUsed(Ticket ticket) {
        return new TicketYaException(TicketErrorCode.TICKET_ALREADY_USED,
                "La entrada %d ya se usó".formatted(ticket.getId()));
    }
}
