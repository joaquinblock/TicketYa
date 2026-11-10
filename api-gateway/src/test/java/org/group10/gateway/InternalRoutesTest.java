package org.group10.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InternalRoutesTest {

    private static final List<String> PATHS_RECEIVED_BY_PAYMENT_SERVICE = new CopyOnWriteArrayList<>();
    private static final HttpServer PAYMENT_SERVICE = startPaymentServiceAnsweringOk();

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void routePaymentServiceToTheStub(DynamicPropertyRegistry registry) {
        registry.add("PAYMENT_SERVICE_URL", () -> "http://localhost:" + PAYMENT_SERVICE.getAddress().getPort());
    }

    @BeforeEach
    void forgetPreviousRequests() {
        PATHS_RECEIVED_BY_PAYMENT_SERVICE.clear();
    }

    @AfterAll
    static void stopPaymentService() {
        PAYMENT_SERVICE.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/payments", "/internal/refunds"})
    void post_internalRoute_answers404WithoutReachingPaymentService(String path) {
        assertThat(statusOf(HttpMethod.POST, path)).isEqualTo(404);
        assertThat(PATHS_RECEIVED_BY_PAYMENT_SERVICE).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/payments", "/internal/refunds"})
    void get_internalRoute_answers404WithoutReachingPaymentService(String path) {
        assertThat(statusOf(HttpMethod.GET, path)).isEqualTo(404);
        assertThat(PATHS_RECEIVED_BY_PAYMENT_SERVICE).isEmpty();
    }

    @Test
    void get_publicPaymentsRoute_isForwardedToPaymentService() {
        assertThat(statusOf(HttpMethod.GET, "/api/payments?orderId=1")).isEqualTo(200);
        assertThat(PATHS_RECEIVED_BY_PAYMENT_SERVICE).containsExactly("/api/payments");
    }

    private int statusOf(HttpMethod method, String path) {
        RestClient.RequestBodySpec request = RestClient.create("http://localhost:" + port)
                .method(method)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);

        return request.exchange((sent, response) -> response.getStatusCode().value());
    }

    private static HttpServer startPaymentServiceAnsweringOk() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                PATHS_RECEIVED_BY_PAYMENT_SERVICE.add(exchange.getRequestURI().getPath());
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
