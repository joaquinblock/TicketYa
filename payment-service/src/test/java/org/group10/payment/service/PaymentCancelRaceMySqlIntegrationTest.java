package org.group10.payment.service;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentCancelRaceMySqlIntegrationTest {

    private static final long TIMEOUT_SECONDS = 20;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    private record HttpAnswer(int status, String body) {
    }

    @Test
    void cancel_confirmedWhileAChargeRetryIsAtTheGateway_keepsTheCancellationAndTheChargeDoesNotOverwriteIt()
            throws Exception {
        Long orderId = uniqueOrderId();
        String key = uniqueKey();
        paymentRepository.saveAndFlush(pendingPayment(orderId, key));
        CountDownLatch chargeReached = new CountDownLatch(1);
        CountDownLatch chargeReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            chargeReached.countDown();
            assertThat(chargeReleased.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(paymentGateway).charge(any());

        HttpAnswer cancelAnswer;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<HttpAnswer> chargeRetry = executor.submit(() -> postPayment(chargeBody(orderId, key)));
            assertThat(chargeReached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            cancelAnswer = postCancel(key);
            chargeReleased.countDown();
            chargeRetry.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(cancelAnswer.status()).isEqualTo(200);
        assertThat(cancelAnswer.body()).contains("\"status\":\"CANCELLED\"");
        assertThat(paymentRepository.findByIdempotencyKey(key)).hasValueSatisfying(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(payment.getActiveOrderId()).isNull();
        });
    }

    @Test
    void cancel_whileAChargeRetryCommitsTheApproval_answersApprovedAndDoesNotOverwriteIt() throws Exception {
        Long orderId = uniqueOrderId();
        String key = uniqueKey();
        paymentRepository.saveAndFlush(pendingPayment(orderId, key));
        CountDownLatch cancelReached = new CountDownLatch(1);
        CountDownLatch cancelReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            cancelReached.countDown();
            assertThat(cancelReleased.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(paymentGateway).cancel(any());

        HttpAnswer cancelAnswer;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<HttpAnswer> cancel = executor.submit(() -> postCancel(key));
            assertThat(cancelReached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            HttpAnswer chargeAnswer = postPayment(chargeBody(orderId, key));
            assertThat(chargeAnswer.status()).isEqualTo(201);
            cancelReleased.countDown();
            cancelAnswer = cancel.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        assertThat(cancelAnswer.status()).isEqualTo(200);
        assertThat(cancelAnswer.body()).contains("\"status\":\"APPROVED\"");
        assertThat(paymentRepository.findByIdempotencyKey(key)).hasValueSatisfying(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getGatewayId()).isNotNull();
        });
    }

    private HttpAnswer postPayment(String body) {
        return RestClient.create("http://localhost:" + port).post()
                .uri("/internal/payments").header("X-Internal-Secret", "dev-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpAnswer(response.getStatusCode().value(), response.bodyTo(String.class)));
    }

    private HttpAnswer postCancel(String key) {
        return RestClient.create("http://localhost:" + port).post()
                .uri("/internal/payments/cancel").header("X-Internal-Secret", "dev-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"idempotencyKey\":\"%s\"}".formatted(key))
                .exchange((request, response) ->
                        new HttpAnswer(response.getStatusCode().value(), response.bodyTo(String.class)));
    }

    private Payment pendingPayment(Long orderId, String key) {
        return Payment.builder()
                .orderId(orderId)
                .idempotencyKey(key)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .amount(new BigDecimal("25000.00"))
                .status(PaymentStatus.PENDING)
                .build();
    }

    private String chargeBody(Long orderId, String key) {
        return """
                {"orderId": %d, "amount": 25000.00, "paymentMethod": "CREDIT_CARD", "idempotencyKey": "%s"}"""
                .formatted(orderId, key);
    }

    private Long uniqueOrderId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }

    private String uniqueKey() {
        return UUID.randomUUID().toString();
    }
}
