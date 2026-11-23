package org.group10.queue.service;

import org.group10.common.exception.TicketYaException;
import org.group10.queue.client.EventClient;
import org.group10.queue.dto.QueueEntryResponse;
import org.group10.queue.entity.QueueEntrySnapshot;
import org.group10.queue.exception.QueueErrorCode;
import org.group10.queue.repository.QueueRepository;
import org.group10.queue.security.QueueTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueStatusServiceTest {

    private static final Long EVENT_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final Instant ENABLED_AT = Instant.parse("2026-11-22T13:00:30Z");
    private static final Instant ENABLED_UNTIL = ENABLED_AT.plusSeconds(600);

    @Mock
    private EventClient eventClient;

    @Mock
    private QueueRepository queueRepository;

    @Mock
    private QueueTokenProvider queueTokenProvider;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(eventClient, queueRepository,
                Clock.fixed(ENABLED_AT.plusSeconds(1), ZoneOffset.UTC), queueTokenProvider);
    }

    @Test
    void getStatus_customerHasNotJoined_throwsNotFound() {
        when(queueRepository.findCurrent(EVENT_ID, USER_ID, ENABLED_AT.plusSeconds(1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queueService.getStatus(EVENT_ID, USER_ID))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(QueueErrorCode.QUEUE_ENTRY_NOT_FOUND);

        verify(queueTokenProvider, never()).issue(EVENT_ID, USER_ID, "7:1", ENABLED_AT, ENABLED_UNTIL);
    }

    @Test
    void getStatus_waitingCustomer_returnsPositionWithoutTurn() {
        when(queueRepository.findCurrent(EVENT_ID, USER_ID, ENABLED_AT.plusSeconds(1)))
                .thenReturn(Optional.of(new QueueEntrySnapshot("7:1", "WAITING", 3, null, null, null)));

        QueueEntryResponse response = queueService.getStatus(EVENT_ID, USER_ID);

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.position()).isEqualTo(3);
        assertThat(response.queueToken()).isNull();
        assertThat(response.enabledUntil()).isNull();
    }

    @Test
    void getStatus_enabledCustomerReusesStoredTurn() {
        when(queueRepository.findCurrent(EVENT_ID, USER_ID, ENABLED_AT.plusSeconds(1)))
                .thenReturn(Optional.of(new QueueEntrySnapshot(
                        "7:1", "ENABLED", null, ENABLED_UNTIL.toEpochMilli(), ENABLED_AT.toEpochMilli(), null)))
                .thenReturn(Optional.of(new QueueEntrySnapshot(
                        "7:1", "ENABLED", null, ENABLED_UNTIL.toEpochMilli(), ENABLED_AT.toEpochMilli(),
                        "stored-token")));
        when(queueTokenProvider.issue(EVENT_ID, USER_ID, "7:1", ENABLED_AT, ENABLED_UNTIL))
                .thenReturn("new-token");
        when(queueRepository.storeQueueTokenIfAbsent(EVENT_ID, "7:1", "new-token")).thenReturn("stored-token");

        QueueEntryResponse first = queueService.getStatus(EVENT_ID, USER_ID);
        QueueEntryResponse second = queueService.getStatus(EVENT_ID, USER_ID);

        assertThat(first.queueToken()).isEqualTo("stored-token");
        assertThat(second.queueToken()).isEqualTo("stored-token");
        assertThat(first.enabledUntil()).isEqualTo(ENABLED_UNTIL);
        assertThat(second.enabledUntil()).isEqualTo(ENABLED_UNTIL);
        verify(queueTokenProvider).issue(EVENT_ID, USER_ID, "7:1", ENABLED_AT, ENABLED_UNTIL);
    }
}
