package org.group10.queue.service;

import org.group10.common.exception.TicketYaException;
import org.group10.queue.client.EventClient;
import org.group10.queue.client.dto.QueueEventResponse;
import org.group10.queue.dto.QueueEntryResponse;
import org.group10.queue.entity.QueueEntrySnapshot;
import org.group10.queue.exception.QueueErrorCode;
import org.group10.queue.repository.QueueRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class QueueService {

    private final EventClient eventClient;
    private final QueueRepository queueRepository;
    private final Clock clock;

    public QueueService(EventClient eventClient, QueueRepository queueRepository, Clock clock) {
        this.eventClient = eventClient;
        this.queueRepository = queueRepository;
        this.clock = clock;
    }

    public QueueEntryResponse join(Long eventId, Long userId) {
        QueueEventResponse event = eventClient.findEventForQueue(eventId);
        if (!event.highDemand()) {
            throw new TicketYaException(QueueErrorCode.EVENT_NOT_HIGH_DEMAND,
                    "El evento %d no es de alta demanda".formatted(eventId));
        }
        if (event.saleStart() == null || event.saleStart().isAfter(LocalDateTime.now(clock))) {
            throw new TicketYaException(QueueErrorCode.SALE_NOT_STARTED,
                    "La venta del evento %d todavía no abrió".formatted(eventId));
        }

        QueueEntrySnapshot entry = queueRepository.join(eventId, userId, clock.instant());
        return new QueueEntryResponse(entry.status(), entry.position(), null, null);
    }
}
