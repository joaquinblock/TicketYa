package org.group10.gateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityHeadersTest {

    private static final List<Headers> HEADERS_RECEIVED_BY_EVENT_SERVICE = new CopyOnWriteArrayList<>();
    private static final HttpServer EVENT_SERVICE_AND_JWKS = startStub();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void routeToTheStub(DynamicPropertyRegistry registry) {
        String stub = "http://localhost:" + EVENT_SERVICE_AND_JWKS.getAddress().getPort();
        registry.add("EVENT_SERVICE_URL", () -> stub);
        registry.add("ticketya.jwt.jwk-set-uri", () -> stub + "/.well-known/jwks.json");
    }

    @BeforeEach
    void forgetPreviousRequests() {
        HEADERS_RECEIVED_BY_EVENT_SERVICE.clear();
    }

    @AfterAll
    static void stopStub() {
        EVENT_SERVICE_AND_JWKS.stop(0);
    }

    @Test
    void withoutToken_passesWithoutIdentity() {
        ResponseEntity<String> response = get(Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(identityReceived()).isEmpty();
    }

    @Test
    void withoutToken_identityHeadersSentByTheClientAreRemoved() {
        get(Map.of("X-User-Id", "3", "x-user-role", "ADMIN", "X-USER-PERMISSIONS", "EVENT_ADMIN",
                "X-Organization-Id", "2"));

        assertThat(identityReceived()).isEmpty();
    }

    @Test
    void withValidClientToken_forwardsItsIdentityAsHeaders() {
        ResponseEntity<String> response = get(Map.of("Authorization", "Bearer " + TestTokens.client()));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(identityReceived()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "X-User-Id", "7",
                "X-User-Role", "CLIENT",
                "X-User-Permissions", "EVENT_SEARCH,TICKET_PURCHASE"));
    }

    @Test
    void withValidOrganizerToken_forwardsItsOrganization() {
        get(Map.of("Authorization", "Bearer " + TestTokens.organizer()));

        assertThat(identityReceived()).containsEntry("X-User-Id", "2")
                .containsEntry("X-User-Permissions", "EVENT_MANAGE,REPORT_VIEW")
                .containsEntry("X-Organization-Id", "1");
    }

    @Test
    void withValidToken_forgedHeadersOfTheClientNeverReachTheService() {
        get(Map.of("Authorization", "Bearer " + TestTokens.client(),
                "X-User-Id", "3", "X-User-Permissions", "EVENT_ADMIN,USER_MANAGE", "X-Organization-Id", "2"));

        assertThat(identityReceived()).containsEntry("X-User-Id", "7")
                .containsEntry("X-User-Permissions", "EVENT_SEARCH,TICKET_PURCHASE")
                .doesNotContainKey("X-Organization-Id");
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void withInvalidToken_answers401WithoutReachingTheService(String authorization) {
        ResponseEntity<String> response = get(Map.of("Authorization", authorization));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).contains("\"error\":\"unauthorized\"");
        assertThat(HEADERS_RECEIVED_BY_EVENT_SERVICE).isEmpty();
    }

    static Stream<String> invalidTokens() {
        String valid = TestTokens.client();
        String[] parts = valid.split("\\.");
        return Stream.of(
                "Bearer not-a-jwt",
                "Bearer ",
                "Bearer " + parts[0] + "." + parts[1] + "x." + parts[2],
                "Bearer " + TestTokens.sign(TestTokens.OTHER_KEY, claims -> claims),
                "Bearer " + TestTokens.sign(TestTokens.AUTH_KEY,
                        claims -> claims.issuedAt(Instant.now().minusSeconds(7200))
                                .expiresAt(Instant.now().minusSeconds(3600))),
                "Bearer " + TestTokens.sign(TestTokens.AUTH_KEY, claims -> claims.issuer("someone-else")),
                "Bearer " + TestTokens.sign(TestTokens.AUTH_KEY, claims -> claims.audience(List.of("other-api"))));
    }

    private ResponseEntity<String> get(Map<String, String> headers) {
        return RestClient.create().get()
                .uri("http://localhost:" + port + "/api/events")
                .headers(h -> headers.forEach(h::add))
                .retrieve()
                .onStatus(status -> true, (request, response) -> { })
                .toEntity(String.class);
    }

    private static Map<String, String> identityReceived() {
        assertThat(HEADERS_RECEIVED_BY_EVENT_SERVICE).hasSize(1);
        Headers received = HEADERS_RECEIVED_BY_EVENT_SERVICE.getFirst();
        Map<String, String> identity = new java.util.HashMap<>();
        for (String name : List.of("X-User-Id", "X-User-Role", "X-User-Permissions", "X-Organization-Id")) {
            List<String> values = received.get(name);
            if (values != null) {
                assertThat(values).as(name).hasSize(1);
                identity.put(name, values.getFirst());
            }
        }
        return identity;
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/.well-known/jwks.json", exchange -> answer(exchange, TestTokens.jwks()));
            server.createContext("/api/events", exchange -> {
                HEADERS_RECEIVED_BY_EVENT_SERVICE.add(exchange.getRequestHeaders());
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
