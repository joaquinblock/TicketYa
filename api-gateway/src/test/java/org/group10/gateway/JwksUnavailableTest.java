package org.group10.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwksUnavailableTest {

    @Value("${local.server.port}")
    private int port;

    private static final AtomicInteger REQUESTS_TO_EVENT_SERVICE = new AtomicInteger();
    private static final HttpServer EVENT_SERVICE = startEventServiceAnsweringOk();

    @DynamicPropertySource
    static void pointToAnAuthServiceThatIsDown(DynamicPropertyRegistry registry) {
        registry.add("EVENT_SERVICE_URL", () -> "http://localhost:" + EVENT_SERVICE.getAddress().getPort());
        registry.add("ticketya.jwt.jwk-set-uri", () -> "http://localhost:" + freePort() + "/.well-known/jwks.json");
    }

    @AfterAll
    static void stopEventService() {
        EVENT_SERVICE.stop(0);
    }

    @Test
    void withToken_authServiceDown_answers503WithoutReachingTheService() {
        ResponseEntity<String> response = get("Bearer " + TestTokens.client());

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).contains("\"error\":\"service_unavailable\"");
        assertThat(REQUESTS_TO_EVENT_SERVICE).hasValue(0);
    }

    @Test
    void withoutToken_authServiceDown_stillPasses() {
        ResponseEntity<String> response = get(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    private ResponseEntity<String> get(String authorization) {
        return RestClient.create().get()
                .uri("http://localhost:" + port + "/api/events")
                .headers(h -> {
                    if (authorization != null) {
                        h.add("Authorization", authorization);
                    }
                })
                .retrieve()
                .onStatus(status -> true, (request, r) -> { })
                .toEntity(String.class);
    }

    private static HttpServer startEventServiceAnsweringOk() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/events", exchange -> {
                REQUESTS_TO_EVENT_SERVICE.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
