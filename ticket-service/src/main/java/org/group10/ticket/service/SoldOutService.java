package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.entity.EventSale;
import org.group10.ticket.entity.EventSaleStatus;
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

    public void notifyPendingAvailabilityChanges() {
        eventSaleRepository.findByAvailabilityNotificationPendingTrue().forEach(this::notifyLoggingAnyFailure);
    }

    private boolean nothingIsLeftToSell(Long eventId) {
        return eventSeatRepository.countByEventIdAndStatusNot(eventId, EventSeatStatus.SOLD) == 0
                && eventSectorStockRepository.countSectorsWithFreePlaces(eventId) == 0
                && ticketRepository.countReservedTicketsOfEvent(eventId) == 0;
    }

    private void notifyLoggingAnyFailure(EventSale sale) {
        try {
            notifyCurrentAvailability(sale);
            clearNotification(sale);
        } catch (HttpClientErrorException rejected) {
            log.warn("El event-service rechazó el cambio a {} del evento {}: {}", sale.getStatus(), sale.getEventId(),
                    rejected.getStatusCode());
            clearNotification(sale);
        } catch (RuntimeException failure) {
            log.error("No se pudo avisar el cambio a {} del evento {}", sale.getStatus(), sale.getEventId(), failure);
        }
    }

    private void notifyCurrentAvailability(EventSale sale) {
        if (sale.getStatus() == EventSaleStatus.SOLD_OUT) {
            eventClient.markEventSoldOut(sale.getEventId());
        } else if (sale.getStatus() == EventSaleStatus.PUBLISHED) {
            eventClient.markEventBackOnSale(sale.getEventId());
        }
    }

    private void clearNotification(EventSale sale) {
        transactions.executeWithoutResult(transaction ->
                eventSaleRepository.clearAvailabilityNotification(sale.getEventId(), sale.getStatus()));
    }
}
