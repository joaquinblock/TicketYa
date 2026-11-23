package org.group10.queue.client;

import org.group10.queue.repository.QueueRepository;
import org.group10.queue.security.QueueTokenProvider;
import org.group10.queue.service.QueueService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class EventClientHttpIntegrationTest {

    private static final String EVENT_SERVICE_URL = "http://event-service";

    @Test
    void join_eventService404DoesNotCreateQueueEntry() {
        RestClient.Builder builder = RestClient.builder().baseUrl(EVENT_SERVICE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EventClient eventClient = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(builder.build()))
                .build()
                .createClient(EventClient.class);
        QueueRepository queueRepository = mock(QueueRepository.class);
        QueueTokenProvider queueTokenProvider = mock(QueueTokenProvider.class);
        QueueService queueService = new QueueService(eventClient, queueRepository,
                Clock.fixed(Instant.parse("2026-11-22T13:00:00Z"), ZoneOffset.UTC), queueTokenProvider);
        server.expect(requestTo(EVENT_SERVICE_URL + "/api/events/10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":"event_not_found","message":"No existe el evento 10","status":404}
                                """));

        assertThatThrownBy(() -> queueService.join(10L, 7L))
                .hasMessageContaining("No existe el evento 10");

        server.verify();
        verifyNoInteractions(queueRepository, queueTokenProvider);
    }
}
