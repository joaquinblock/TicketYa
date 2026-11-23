package org.group10.queue.service;

import org.group10.queue.client.EventClient;
import org.group10.common.exception.TicketYaException;
import org.group10.queue.exception.QueueErrorCode;
import org.group10.queue.repository.QueueRepository;
import org.group10.queue.security.QueueTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueEventNotFoundServiceTest {

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
    void join_eventDoesNotExist_propagatesNotFoundWithoutCreatingEntry() {
        when(eventClient.findEventForQueue(10L)).thenThrow(eventNotFound());

        assertThatThrownBy(() -> queueService.join(10L, 7L))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(QueueErrorCode.EVENT_NOT_FOUND);

        verifyNoInteractions(queueRepository, queueTokenProvider);
    }

    static RestClientResponseException eventNotFound() {
        byte[] body = """
                {"error":"event_not_found","message":"No existe el evento 10","status":404}
                """.getBytes(StandardCharsets.UTF_8);
        return new RestClientResponseException("Event not found", HttpStatus.NOT_FOUND.value(), "Not Found",
                HttpHeaders.EMPTY, body, StandardCharsets.UTF_8);
    }
}
