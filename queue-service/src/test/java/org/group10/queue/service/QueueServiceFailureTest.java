package org.group10.queue.service;

import org.group10.queue.client.EventClient;
import org.group10.queue.repository.QueueRepository;
import org.group10.queue.security.QueueTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceFailureTest {

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
                Clock.fixed(Instant.parse("2026-11-22T13:00:00Z"), ZoneOffset.UTC), queueTokenProvider);
    }

    @Test
    void join_eventServiceTimesOut_failsWithoutCreatingQueueEntry() {
        when(eventClient.findEventForQueue(10L)).thenThrow(new ResourceAccessException("Read timed out"));

        assertThatThrownBy(() -> queueService.join(10L, 7L))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Read timed out");

        verifyNoInteractions(queueRepository, queueTokenProvider);
    }
}
