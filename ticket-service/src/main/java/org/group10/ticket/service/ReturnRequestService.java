package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.client.PaymentClient;
import org.group10.ticket.client.dto.RefundRequest;
import org.group10.ticket.dto.ReturnRequestPageResponse;
import org.group10.ticket.dto.ReturnRequestResponse;
import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.EventSaleStatus;
import org.group10.ticket.entity.ReturnRequest;
import org.group10.ticket.entity.ReturnRequestStatus;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.mapper.ReturnRequestMapper;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.ReturnRequestRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestService {

    private static final Duration MINIMUM_NOTICE_BEFORE_THE_EVENT = Duration.ofHours(72);
    private static final Set<EventSaleStatus> SALE_STATUSES_ACCEPTING_RETURNS =
            Set.of(EventSaleStatus.PUBLISHED, EventSaleStatus.SOLD_OUT, EventSaleStatus.SUSPENDED);
    private static final Sort OLDEST_FIRST = Sort.by("requestedAt", "id");
    private static final String RETURN_REQUEST_REASON = "RETURN_REQUEST";
    private static final long ONE_PLACE = 1;

    private final ReturnRequestRepository returnRequestRepository;
    private final TicketRepository ticketRepository;
    private final EventSaleRepository eventSaleRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectorStockRepository eventSectorStockRepository;
    private final PaymentClient paymentClient;
    private final TransactionOperations transactions;
    private final Clock clock;

    public ReturnRequestResponse requestReturn(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findTicketOfCustomerWithItsOrder(ticketId, userId)
                .orElseThrow(() -> new TicketYaException(TicketErrorCode.TICKET_NOT_FOUND,
                        "No se encontró la entrada con id %d".formatted(ticketId)));
        ensureTicketCanBeReturned(ticket);

        try {
            return ReturnRequestMapper.toResponse(returnRequestRepository.save(pendingRequest(ticket, userId)));
        } catch (DataIntegrityViolationException duplicatedPendingRequest) {
            throw new TicketYaException(TicketErrorCode.RETURN_REQUEST_ALREADY_PENDING,
                    "La entrada %d ya tiene una solicitud pendiente".formatted(ticketId));
        }
    }

    public ReturnRequestPageResponse findByStatus(ReturnRequestStatus status, int page, int size) {
        return ReturnRequestMapper.toPageResponse(
                returnRequestRepository.findByStatus(status, PageRequest.of(page, size, OLDEST_FIRST)));
    }

    public ReturnRequestResponse approve(Long administratorId, Long requestId) {
        ReturnRequest request = findPendingRequest(requestId);
        ensureTicketCanBeRefunded(request.getTicket());

        paymentClient.refund(refundOf(request));

        if (!Boolean.TRUE.equals(transactions.execute(transaction -> confirmApproval(administratorId, request)))) {
            throw requestAlreadyResolved(requestId);
        }
        return ReturnRequestMapper.toResponse(findRequest(requestId));
    }

    public ReturnRequestResponse reject(Long administratorId, Long requestId) {
        findPendingRequest(requestId);

        Integer resolvedRequests = transactions.execute(transaction -> returnRequestRepository.resolvePendingRequest(
                requestId, ReturnRequestStatus.REJECTED, administratorId, LocalDateTime.now(clock)));
        if (resolvedRequests == null || resolvedRequests == 0) {
            throw requestAlreadyResolved(requestId);
        }
        return ReturnRequestMapper.toResponse(findRequest(requestId));
    }

    private void ensureTicketCanBeReturned(Ticket ticket) {
        boolean canBeReturned = ticket.getStatus() == TicketStatus.ISSUED
                && eventSaleRepository.findById(ticket.getOrder().getEventId()).filter(this::acceptsReturnsNow).isPresent();

        if (!canBeReturned) {
            throw new TicketYaException(TicketErrorCode.REFUND_NOT_ALLOWED,
                    "La entrada %d no admite devolución".formatted(ticket.getId()));
        }
    }

    private boolean acceptsReturnsNow(EventSale sale) {
        return SALE_STATUSES_ACCEPTING_RETURNS.contains(sale.getStatus())
                && !LocalDateTime.now(clock).isAfter(sale.getDateTime().minus(MINIMUM_NOTICE_BEFORE_THE_EVENT));
    }

    private ReturnRequest pendingRequest(Ticket ticket, Long userId) {
        return ReturnRequest.builder()
                .ticket(ticket)
                .requestedBy(userId)
                .requestedAt(LocalDateTime.now(clock))
                .status(ReturnRequestStatus.PENDING)
                .build();
    }

    private ReturnRequest findPendingRequest(Long requestId) {
        ReturnRequest request = findRequest(requestId);
        if (request.getStatus() != ReturnRequestStatus.PENDING) {
            throw requestAlreadyResolved(requestId);
        }
        return request;
    }

    private ReturnRequest findRequest(Long requestId) {
        return returnRequestRepository.findWithTicketById(requestId)
                .orElseThrow(() -> new TicketYaException(TicketErrorCode.RETURN_REQUEST_NOT_FOUND,
                        "No se encontró la solicitud de devolución con id %d".formatted(requestId)));
    }

    private static void ensureTicketCanBeRefunded(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.USED) {
            throw new TicketYaException(TicketErrorCode.TICKET_ALREADY_USED,
                    "La entrada %d ya se usó".formatted(ticket.getId()));
        }
        if (ticket.getStatus() != TicketStatus.ISSUED) {
            throw new TicketYaException(TicketErrorCode.REFUND_NOT_ALLOWED,
                    "La entrada %d está %s y no se puede reintegrar".formatted(ticket.getId(), ticket.getStatus()));
        }
    }

    private static RefundRequest refundOf(ReturnRequest request) {
        Ticket ticket = request.getTicket();
        return new RefundRequest(ticket.getId(), ticket.getOrder().getId(), ticket.getPrice(), RETURN_REQUEST_REASON,
                request.getId());
    }

    private boolean confirmApproval(Long administratorId, ReturnRequest request) {
        Ticket ticket = request.getTicket();
        if (ticketRepository.markIssuedTicketAsRefunded(ticket.getId()) == 1) {
            releasePlaceOf(ticket);
        } else {
            log.error("La entrada {} se reintegró pero ya no estaba ISSUED al confirmar la solicitud {}",
                    ticket.getId(), request.getId());
        }
        return returnRequestRepository.resolvePendingRequest(request.getId(), ReturnRequestStatus.APPROVED,
                administratorId, LocalDateTime.now(clock)) == 1;
    }

    private void releasePlaceOf(Ticket ticket) {
        Long eventId = ticket.getOrder().getEventId();
        if (ticket.getSeatId() != null) {
            eventSeatRepository.releaseSoldSeat(eventId, ticket.getSeatId());
        } else {
            eventSectorStockRepository.releasePlaces(ticket.getEventSectorId(), ONE_PLACE);
        }
        eventSaleRepository.markSoldOutSaleAsPublished(eventId);
    }

    private static TicketYaException requestAlreadyResolved(Long requestId) {
        return new TicketYaException(TicketErrorCode.REFUND_NOT_ALLOWED,
                "La solicitud de devolución %d ya fue resuelta".formatted(requestId));
    }
}
