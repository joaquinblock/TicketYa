package org.group10.queue.service;

import org.group10.common.exception.TicketYaException;
import org.group10.queue.client.EventClient;
import org.group10.queue.client.dto.QueueEventResponse;
import org.group10.queue.dto.QueueEntryResponse;
import org.group10.queue.entity.QueueEntrySnapshot;
import org.group10.queue.exception.QueueErrorCode;
import org.group10.queue.repository.QueueRepository;
import org.group10.queue.security.QueueTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class QueueService {

    private final EventClient eventClient;
    private final QueueRepository queueRepository;
    private final Clock clock;
    private final Optional<QueueTokenProvider> queueTokenProvider;

    @Autowired
    public QueueService(EventClient eventClient, QueueRepository queueRepository, Clock clock,
                        QueueTokenProvider queueTokenProvider) {
        this.eventClient = eventClient;
        this.queueRepository = queueRepository;
        this.clock = clock;
        this.queueTokenProvider = Optional.of(queueTokenProvider);
    }

    QueueService(EventClient eventClient, QueueRepository queueRepository, Clock clock) {
        this.eventClient = eventClient;
        this.queueRepository = queueRepository;
        this.clock = clock;
        this.queueTokenProvider = Optional.empty();
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
        if ("ENABLED".equals(entry.status())) {
            return getStatus(eventId, userId);
        }
        return new QueueEntryResponse(entry.status(), entry.position(), null, null);
    }

    public QueueEntryResponse getStatus(Long eventId, Long userId) {
        Instant now = clock.instant();
        QueueEntrySnapshot entry = queueRepository.findCurrent(eventId, userId, now)
                .orElseThrow(() -> new TicketYaException(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND,
                        "El cliente %d no ingresó a la fila del evento %d".formatted(userId, eventId)));
        if (!"ENABLED".equals(entry.status())) {
            return new QueueEntryResponse(entry.status(), entry.position(), null, null);
        }

        Instant enabledAt = Instant.ofEpochMilli(entry.enabledAtEpochMillis());
        Instant enabledUntil = Instant.ofEpochMilli(entry.enabledUntilEpochMillis());
        String queueToken = entry.queueToken();
        if (queueToken == null) {
            String issuedToken = queueTokenProvider.orElseThrow(() ->
                            new IllegalStateException("QueueTokenProvider no está configurado"))
                    .issue(eventId, userId, entry.entryId(), enabledAt, enabledUntil);
            queueToken = queueRepository.storeQueueTokenIfAbsent(eventId, entry.entryId(), issuedToken);
        }
        return new QueueEntryResponse(entry.status(), null, queueToken, enabledUntil);
    }
}
