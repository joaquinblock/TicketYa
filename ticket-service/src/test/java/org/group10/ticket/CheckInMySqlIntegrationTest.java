package org.group10.ticket;

import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.OrderStatus;
import org.group10.ticket.entity.PaymentMethod;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.repository.OrderRepository;
import org.group10.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un QR no puede ingresar dos veces (consigna 8), aun con varios lectores escaneándolo a la vez contra la base real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ticketya.reservation.expiration-check=PT1H")
@ActiveProfiles("test")
class CheckInMySqlIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() + 900_000_000);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final int SCANNERS = 16;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TicketRepository ticketRepository;

    private RestClient client;
    private long eventId;
    private long ticketId;
    private String qrCode;

    private record HttpResult(int status, Map<String, Object> body) {

        String error() {
            return (String) body.get("error");
        }
    }

    @BeforeEach
    void issueATicket() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        eventId = IDS.incrementAndGet();
        qrCode = UUID.randomUUID().toString();

        Order order = orderRepository.save(Order.builder()
                .userId(IDS.incrementAndGet())
                .eventId(eventId)
                .status(OrderStatus.PAID)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .total(new BigDecimal("1000.00"))
                .idempotencyKey(UUID.randomUUID().toString())
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build());
        ticketId = ticketRepository.save(Ticket.builder()
                .order(order)
                .eventSectorId(IDS.incrementAndGet())
                .sectorName("Platea")
                .row("A")
                .number(1)
                .price(new BigDecimal("1000.00"))
                .qrCode(qrCode)
                .status(TicketStatus.ISSUED)
                .build()).getId();
    }

    @Test
    void checkIn_sameQrScannedAtTheSameTime_entersExactlyOnce() throws Exception {
        List<HttpResult> results = scanAtTheSameTime();

        assertThat(results).filteredOn(result -> result.status() == 200).hasSize(1);
        assertThat(results).filteredOn(result -> result.status() != 200)
                .hasSize(SCANNERS - 1)
                .allSatisfy(result -> {
                    assertThat(result.status()).isEqualTo(409);
                    assertThat(result.error()).isEqualTo("ticket_already_used");
                });

        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(ticket.getUsedAt()).isNotNull();
        assertThat(ticket.getValidatedBy()).isBetween(1L, (long) SCANNERS);
    }

    @Test
    void checkIn_secondScanAfterTheFirst_isAlreadyUsed() {
        assertThat(scan(1L).status()).isEqualTo(200);

        HttpResult second = scan(2L);

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.error()).isEqualTo("ticket_already_used");
        assertThat(ticketRepository.findById(ticketId).orElseThrow().getValidatedBy()).isEqualTo(1L);
    }

    private HttpResult scan(long staffId) {
        return client.post().uri("/api/tickets/check-in")
                .header("X-User-Id", Long.toString(staffId)).header("X-User-Permissions", "TICKET_VALIDATE")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"eventId": %d, "qrCode": "%s"}""".formatted(eventId, qrCode))
                .exchange((request, response) ->
                        new HttpResult(response.getStatusCode().value(), response.bodyTo(JSON_OBJECT)));
    }

    /**
     * Cada lector es un usuario de acceso distinto (1..SCANNERS); todos salen juntos cuando están listos.
     */
    private List<HttpResult> scanAtTheSameTime() throws Exception {
        CountDownLatch ready = new CountDownLatch(SCANNERS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(SCANNERS)) {
            List<Future<HttpResult>> scans = new ArrayList<>();
            for (long staffId = 1; staffId <= SCANNERS; staffId++) {
                long scanner = staffId;
                scans.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return scan(scanner);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpResult> results = new ArrayList<>();
            for (Future<HttpResult> scan : scans) {
                results.add(scan.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
