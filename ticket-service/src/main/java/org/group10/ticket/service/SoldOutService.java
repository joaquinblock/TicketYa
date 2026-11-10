package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.entity.EventSeatStatus;
import org.group10.ticket.repository.EventSaleRepository;
import org.group10.ticket.repository.EventSeatRepository;
import org.group10.ticket.repository.EventSectorStockRepository;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.HttpClientErrorException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SoldOutService {

    private final EventSaleRepository eventSaleRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectorStockRepository eventSectorStockRepository;
    private final TicketRepository ticketRepository;
    private final EventClient eventClient;
    private final TransactionOperations transactions;

    public void markEventSoldOutIfNothingIsLeft(Long eventId) {
        try {
            transactions.executeWithoutResult(transaction -> {
                if (nothingIsLeftToSell(eventId)) {
                    eventSaleRepository.markPublishedSaleAsSoldOut(eventId);
                }
            });
        } catch (RuntimeException failure) {
            log.error("No se pudo revisar si el evento {} se agotó", eventId, failure);
        }
    }

    public void notifyPendingSoldOutEvents() {
        eventSaleRepository.findEventIdsWithPendingSoldOutNotification().forEach(this::notifyLoggingAnyFailure);
    }

    private boolean nothingIsLeftToSell(Long eventId) {
        return eventSeatRepository.countByEventIdAndStatusNot(eventId, EventSeatStatus.SOLD) == 0
                && eventSectorStockRepository.countSectorsWithFreePlaces(eventId) == 0
                && ticketRepository.countReservedTicketsOfEvent(eventId) == 0;
    }

    private void notifyLoggingAnyFailure(Long eventId) {
        try {
            eventClient.markEventSoldOut(eventId);
            clearNotification(eventId);
        } catch (HttpClientErrorException rejected) {
            log.warn("El event-service rechazó agotar el evento {}: {}", eventId, rejected.getStatusCode());
            clearNotification(eventId);
        } catch (RuntimeException failure) {
            log.error("No se pudo avisar que el evento {} se agotó", eventId, failure);
        }
    }

    private void clearNotification(Long eventId) {
        transactions.executeWithoutResult(transaction -> eventSaleRepository.clearSoldOutNotification(eventId));
    }
}
