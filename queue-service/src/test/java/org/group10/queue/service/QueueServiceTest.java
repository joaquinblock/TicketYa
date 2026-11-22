package org.group10.queue.service;

import org.group10.common.exception.TicketYaException;
import org.group10.queue.client.EventClient;
import org.group10.queue.client.dto.QueueEventResponse;
import org.group10.queue.exception.QueueErrorCode;
import org.group10.queue.repository.QueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    private static final Long EVENT_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T13:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EventClient eventClient;

    @Mock
    private QueueRepository queueRepository;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(eventClient, queueRepository, CLOCK);
    }

    @Test
    void join_nonHighDemandEvent_throwsConflictWithoutCreatingEntry() {
        when(eventClient.findEventForQueue(EVENT_ID))
                .thenReturn(new QueueEventResponse(EVENT_ID, false, LocalDateTime.of(2026, 11, 22, 12, 0)));

        assertThatThrownBy(() -> queueService.join(EVENT_ID, USER_ID))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(QueueErrorCode.EVENT_NOT_HIGH_DEMAND);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void join_beforeSaleStart_throwsConflictWithoutCreatingEntry() {
        when(eventClient.findEventForQueue(EVENT_ID))
                .thenReturn(new QueueEventResponse(EVENT_ID, true, LocalDateTime.of(2026, 11, 22, 13, 1)));

        assertThatThrownBy(() -> queueService.join(EVENT_ID, USER_ID))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(QueueErrorCode.SALE_NOT_STARTED);
        verifyNoInteractions(queueRepository);
    }
}
