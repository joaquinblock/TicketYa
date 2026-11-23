package org.group10.gateway;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.group10.common.logging.CorrelationIdFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El id de correlación nace en el api-gateway: lo devuelve, lo reenvía al servicio y registra cada request, también
 * los que rechaza.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorrelationIdTest {

    private static final String HEADER_NAME = "X-Correlation-ID";
    private static final List<Headers> HEADERS_RECEIVED_BY_EVENT_SERVICE = new CopyOnWriteArrayList<>();
    private static final HttpServer EVENT_SERVICE_AND_JWKS = startStub();

    @Value("${local.server.port}")
    private int port;

    private Logger logger;
    private ListAppender<ILoggingEvent> requestLogs;

    @DynamicPropertySource
    static void routeToTheStub(DynamicPropertyRegistry registry) {
        String stub = "http://localhost:" + EVENT_SERVICE_AND_JWKS.getAddress().getPort();
        registry.add("EVENT_SERVICE_URL", () -> stub);
        registry.add("ticketya.jwt.jwk-set-uri", () -> stub + "/.well-known/jwks.json");
    }

    @BeforeEach
    void captureRequestLogs() {
        HEADERS_RECEIVED_BY_EVENT_SERVICE.clear();
        logger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        requestLogs = new ListAppender<>();
        requestLogs.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        requestLogs.start();
        logger.addAppender(requestLogs);
    }

    @AfterEach
    void stopCapturing() {
        logger.detachAppender(requestLogs);
        requestLogs.stop();
    }

    @AfterAll
    static void stopStub() {
        EVENT_SERVICE_AND_JWKS.stop(0);
    }

    @Test
    void request_withoutId_generatesOneAndForwardsTheSame() {
        ResponseEntity<String> response = get(Map.of());

        String id = response.getHeaders().getFirst(HEADER_NAME);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(idsReceivedByTheService()).containsExactly(id);
    }

    @Test
    void request_serviceAnswersWithTheId_theClientGetsItOnce() {
        ResponseEntity<String> response = get(Map.of(HEADER_NAME, "demo-compra-1"));

        assertThat(response.getHeaders().get(HEADER_NAME)).containsExactly("demo-compra-1");
    }

    @Test
    void request_withValidId_forwardsItUnchanged() {
        ResponseEntity<String> response = get(Map.of(HEADER_NAME, "demo-compra-1"));

        assertThat(response.getHeaders().getFirst(HEADER_NAME)).isEqualTo("demo-compra-1");
        assertThat(idsReceivedByTheService()).containsExactly("demo-compra-1");
    }

    @Test
    void request_withInvalidId_forwardsTheNewOneOnly() {
        ResponseEntity<String> response = get(Map.of(HEADER_NAME, "con espacios no vale"));

        String id = response.getHeaders().getFirst(HEADER_NAME);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(idsReceivedByTheService()).containsExactly(id);
    }

    @Test
    void request_withValidToken_logsTheUserOfTheTokenNotTheForgedHeader() {
        get(Map.of("Authorization", "Bearer " + TestTokens.client(), "X-User-Id", "3"));

        assertThat(requestLog()).containsEntry("userId", "7").containsEntry("status", 200)
                .containsEntry("endpoint", "/api/events");
    }

    @Test
    void request_withInvalidToken_isLoggedWithItsIdAndThe401() {
        ResponseEntity<String> response = get(Map.of("Authorization", "Bearer not-a-jwt"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HEADER_NAME)).isNotBlank();
        assertThat(requestLog()).containsEntry("status", 401).containsEntry("userId", "anonymous");
        assertThat(requestLogs.list.getFirst().getMDCPropertyMap())
                .containsEntry("correlationId", response.getHeaders().getFirst(HEADER_NAME));
        assertThat(HEADERS_RECEIVED_BY_EVENT_SERVICE).isEmpty();
    }

    @Test
    void request_toAnUnknownRoute_isLogged() {
        RestClient.create().get().uri("http://localhost:" + port + "/api/no-existe")
                .retrieve().onStatus(status -> true, (request, response) -> { }).toBodilessEntity();

        assertThat(requestLog()).containsEntry("status", 404).containsEntry("endpoint", "/api/no-existe");
    }

    private ResponseEntity<String> get(Map<String, String> headers) {
        return RestClient.create().get()
                .uri("http://localhost:" + port + "/api/events")
                .headers(h -> headers.forEach(h::add))
                .retrieve()
                .onStatus(status -> true, (request, response) -> { })
                .toEntity(String.class);
    }

    private Map<String, Object> requestLog() {
        assertThat(requestLogs.list).hasSize(1);
        return requestLogs.list.getFirst().getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }

    private static List<String> idsReceivedByTheService() {
        assertThat(HEADERS_RECEIVED_BY_EVENT_SERVICE).hasSize(1);
        return HEADERS_RECEIVED_BY_EVENT_SERVICE.getFirst().get(HEADER_NAME);
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/.well-known/jwks.json", exchange -> answer(exchange, TestTokens.jwks()));
            server.createContext("/api/events", exchange -> {
                HEADERS_RECEIVED_BY_EVENT_SERVICE.add(exchange.getRequestHeaders());
                String id = exchange.getRequestHeaders().getFirst(HEADER_NAME);
                if (id != null) {
                    exchange.getResponseHeaders().add(HEADER_NAME.toLowerCase(), id);
                }
                answer(exchange, "[]");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void answer(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
