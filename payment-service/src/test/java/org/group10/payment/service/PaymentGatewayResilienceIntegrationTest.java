package org.group10.payment.service;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.gateway.MockGatewayBehavior;
import org.group10.payment.gateway.MockPaymentGateway;
import org.group10.payment.gateway.PaymentGatewayUnavailableException;
import org.group10.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Cobro contra la pasarela simulada a través de la capa de resiliencia real y MySQL.
 * El circuit breaker no se abre acá (mínimo de llamadas alto): su comportamiento está en
 * {@code ResilientPaymentGatewayTest}, y abierto contaminaría a los demás tests del contexto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "payment.gateway.resilience.timeout=300ms",
        "payment.gateway.resilience.wait-between-attempts=1ms",
        "payment.gateway.resilience.minimum-calls=1000",
        "payment.gateway.resilience.sliding-window-size=1000"
})
@ActiveProfiles("test")
class PaymentGatewayResilienceIntegrationTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final MockGatewayBehavior GATEWAY_UP = new MockGatewayBehavior(0, false);
    private static final MockGatewayBehavior GATEWAY_DOWN = new MockGatewayBehavior(0, true);

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private MockPaymentGateway mockPaymentGateway;

    private record HttpAnswer(int status, String body) {
    }

    @AfterEach
    void restoreTheGateway() {
        doCallRealMethod().when(mockPaymentGateway).charge(any());
        mockPaymentGateway.changeBehavior(GATEWAY_UP);
    }

    @Test
    void charge_gatewayFailsOnceAndAnswersOnTheRetry_approvesChargingOnce() {
        Long orderId = uniqueOrderId();
        doThrow(new PaymentGatewayUnavailableException("momentánea")).doCallRealMethod()
                .when(mockPaymentGateway).charge(any());

        HttpAnswer answer = postPayment(paymentBody(orderId, "CREDIT_CARD", uniqueKey()));

        assertThat(answer.status()).isEqualTo(201);
        assertThat(onlyPaymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(mockPaymentGateway, times(2)).charge(any());
    }

    @Test
    void charge_gatewayDown_answers503AfterTheRetriesAndKeepsThePaymentPending() {
        Long orderId = uniqueOrderId();
        mockPaymentGateway.changeBehavior(GATEWAY_DOWN);

        HttpAnswer answer = postPayment(paymentBody(orderId, "CREDIT_CARD", uniqueKey()));

        assertThat(answer.status()).isEqualTo(503);
        assertThat(answer.body()).contains("payment_gateway_unavailable");
        assertThat(onlyPaymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(onlyPaymentOf(orderId).getGatewayId()).isNull();
        verify(mockPaymentGateway, times(MAX_ATTEMPTS)).charge(any());
    }

    @Test
    void charge_gatewaySlowerThanTheTimeout_answers503WithinTheBudgetAndKeepsThePaymentPending() {
        Long orderId = uniqueOrderId();
        mockPaymentGateway.changeBehavior(new MockGatewayBehavior(5_000, false));

        long start = System.nanoTime();
        HttpAnswer answer = postPayment(paymentBody(orderId, "CREDIT_CARD", uniqueKey()));

        assertThat(answer.status()).isEqualTo(503);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        assertThat(onlyPaymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void charge_gatewayRecovers_theRetryWithTheSameKeyChargesOnceWithoutRestarting() {
        Long orderId = uniqueOrderId();
        String body = paymentBody(orderId, "CREDIT_CARD", uniqueKey());
        mockPaymentGateway.changeBehavior(GATEWAY_DOWN);
        assertThat(postPayment(body).status()).isEqualTo(503);

        mockPaymentGateway.changeBehavior(GATEWAY_UP);
        clearInvocations(mockPaymentGateway);
        HttpAnswer afterRecovery = postPayment(body);

        assertThat(afterRecovery.status()).isEqualTo(201);
        assertThat(postPayment(body)).isEqualTo(afterRecovery);
        assertThat(onlyPaymentOf(orderId).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(mockPaymentGateway, times(1)).charge(any());
    }

    @Test
    void charge_attemptWithoutAnswer_doesNotCountAsARejectedAttempt() {
        Long orderId = uniqueOrderId();
        assertThat(postPayment(paymentBody(orderId, "DEBIT_CARD", uniqueKey())).body())
                .contains("\"rejectedAttempts\":1");

        String secondAttempt = paymentBody(orderId, "DEBIT_CARD", uniqueKey());
        mockPaymentGateway.changeBehavior(GATEWAY_DOWN);
        HttpAnswer withoutAnswer = postPayment(secondAttempt);
        mockPaymentGateway.changeBehavior(GATEWAY_UP);
        HttpAnswer rejection = postPayment(secondAttempt);

        assertThat(withoutAnswer.status()).isEqualTo(503);
        assertThat(rejection.status()).isEqualTo(422);
        assertThat(rejection.body()).contains("\"rejectedAttempts\":2");
    }

    private HttpAnswer postPayment(String body) {
        return RestClient.create("http://localhost:" + port).post()
                .uri("/internal/payments").header("X-Internal-Secret", "dev-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpAnswer(response.getStatusCode().value(), response.bodyTo(String.class)));
    }

    private Payment onlyPaymentOf(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderIdOrderByIdAsc(orderId);
        assertThat(payments).hasSize(1);
        return payments.getFirst();
    }

    private String paymentBody(Long orderId, String method, String key) {
        return """
                {"orderId": %d, "amount": 25000.00, "paymentMethod": "%s", "idempotencyKey": "%s"}"""
                .formatted(orderId, method, key);
    }

    private Long uniqueOrderId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }

    private String uniqueKey() {
        return UUID.randomUUID().toString();
    }
}
