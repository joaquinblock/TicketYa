package org.group10.payment.service;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.repository.PaymentRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentIdempotencyIntegrationTest {

    private static final int SIMULTANEOUS_REQUESTS = 8;
    private static final long TIMEOUT_SECONDS = 20;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    private record HttpAnswer(int status, String body) {
    }

    @RepeatedTest(5)
    void charge_sameKeySimultaneously_chargesOnceAndStoresOnePayment() throws Exception {
        Long orderId = uniqueOrderId();
        String body = paymentBody(orderId, "25000.00", "CREDIT_CARD", uniqueKey());
        CountDownLatch ready = new CountDownLatch(SIMULTANEOUS_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(SIMULTANEOUS_REQUESTS)) {
            List<Future<HttpAnswer>> requests = IntStream.range(0, SIMULTANEOUS_REQUESTS)
                    .mapToObj(request -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return postPayment(body);
                    }))
                    .toList();

            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = statusesOf(requests);
            assertThat(statuses).contains(201).allMatch(status -> status == 201 || status == 503);
        }

        assertThat(paymentsOfTheOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED));
        verify(paymentGateway, times(1)).charge(any());
    }

    @Test
    void charge_sameKeyWhileTheFirstRequestIsAtTheGateway_answers503AndThenTheOriginalResult() throws Exception {
        Long orderId = uniqueOrderId();
        String body = paymentBody(orderId, "25000.00", "CREDIT_CARD", uniqueKey());
        CountDownLatch gatewayReached = new CountDownLatch(1);
        CountDownLatch gatewayReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            gatewayReached.countDown();
            assertThat(gatewayReleased.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(paymentGateway).charge(any());

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<HttpAnswer> firstRequest = executor.submit(() -> postPayment(body));
            assertThat(gatewayReached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            HttpAnswer whileCharging = postPayment(body);
            gatewayReleased.countDown();
            HttpAnswer firstAnswer = firstRequest.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            HttpAnswer afterCharging = postPayment(body);

            assertThat(whileCharging.status()).isEqualTo(503);
            assertThat(whileCharging.body()).contains("payment_gateway_unavailable");
            assertThat(firstAnswer.status()).isEqualTo(201);
            assertThat(afterCharging).isEqualTo(firstAnswer);
        }

        assertThat(paymentsOfTheOrder(orderId)).hasSize(1);
        verify(paymentGateway, times(1)).charge(any());
    }

    @Test
    void charge_repeatedRejection_keepsItsOriginalAnswerAfterLaterRejections() {
        Long orderId = uniqueOrderId();
        String firstAttempt = paymentBody(orderId, "25000.00", "DEBIT_CARD", uniqueKey());

        HttpAnswer firstRejection = postPayment(firstAttempt);
        HttpAnswer repeatedRejection = postPayment(firstAttempt);
        HttpAnswer secondRejection = postPayment(paymentBody(orderId, "25000.00", "DEBIT_CARD", uniqueKey()));
        HttpAnswer repeatedAfterSecondRejection = postPayment(firstAttempt);

        assertThat(firstRejection.status()).isEqualTo(422);
        assertThat(firstRejection.body()).contains("\"rejectedAttempts\":1");
        assertThat(repeatedRejection).isEqualTo(firstRejection);
        assertThat(secondRejection.body()).contains("\"rejectedAttempts\":2");
        assertThat(repeatedAfterSecondRejection).isEqualTo(firstRejection);
        assertThat(paymentsOfTheOrder(orderId)).hasSize(2);
        verify(paymentGateway, times(2)).charge(any());
    }

    @Test
    void charge_repeatedRejectionAfterAnApprovedPayment_stillAnswersTheRejection() {
        Long orderId = uniqueOrderId();
        String rejectedAttempt = paymentBody(orderId, "25000.00", "DEBIT_CARD", uniqueKey());

        HttpAnswer rejection = postPayment(rejectedAttempt);
        HttpAnswer approval = postPayment(paymentBody(orderId, "25000.00", "CREDIT_CARD", uniqueKey()));

        assertThat(approval.status()).isEqualTo(201);
        assertThat(postPayment(rejectedAttempt)).isEqualTo(rejection);
    }

    @Test
    void charge_sameKeyWithTheAmountWithoutDecimals_matchesTheStoredColumnAndRepeatsTheAnswer() {
        Long orderId = uniqueOrderId();
        String key = uniqueKey();

        HttpAnswer firstAnswer = postPayment(paymentBody(orderId, "25000.00", "CREDIT_CARD", key));
        HttpAnswer repeatedAnswer = postPayment(paymentBody(orderId, "25000", "CREDIT_CARD", key));

        assertThat(repeatedAnswer).isEqualTo(firstAnswer);
        verify(paymentGateway, times(1)).charge(any());
    }

    @Test
    void charge_sameKeyWithOtherData_answers409AndLeavesThePaymentUntouched() {
        Long orderId = uniqueOrderId();
        String key = uniqueKey();
        String originalAttempt = paymentBody(orderId, "25000.00", "CREDIT_CARD", key);
        HttpAnswer firstAnswer = postPayment(originalAttempt);

        List<HttpAnswer> conflicts = List.of(
                postPayment(paymentBody(uniqueOrderId(), "25000.00", "CREDIT_CARD", key)),
                postPayment(paymentBody(orderId, "25000.01", "CREDIT_CARD", key)),
                postPayment(paymentBody(orderId, "25000.00", "ACCOUNT_MONEY", key)));

        assertThat(conflicts).allSatisfy(conflict -> {
            assertThat(conflict.status()).isEqualTo(409);
            assertThat(conflict.body()).contains("data_conflict");
        });
        assertThat(postPayment(originalAttempt)).isEqualTo(firstAnswer);
        verify(paymentGateway, times(1)).charge(any());
    }

    @Test
    void charge_storedPendingPayment_answers503AndStaysPendingWithoutCharging() {
        Long orderId = uniqueOrderId();
        String key = uniqueKey();
        paymentRepository.saveAndFlush(pendingPayment(orderId, key));

        HttpAnswer answer = postPayment(paymentBody(orderId, "25000.00", "CREDIT_CARD", key));

        assertThat(answer.status()).isEqualTo(503);
        assertThat(answer.body()).contains("payment_gateway_unavailable");
        assertThat(paymentsOfTheOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING));
        verify(paymentGateway, never()).charge(any());
    }

    @Test
    void charge_otherKeyOnAnOrderWithAPendingPayment_answers409WithoutCharging() {
        Long orderId = uniqueOrderId();
        paymentRepository.saveAndFlush(pendingPayment(orderId, uniqueKey()));

        HttpAnswer answer = postPayment(paymentBody(orderId, "25000.00", "CREDIT_CARD", uniqueKey()));

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.body()).contains("data_conflict");
        verify(paymentGateway, never()).charge(any());
    }

    @Test
    void save_duplicatedIdempotencyKey_isRejectedByTheDatabase() {
        String key = uniqueKey();
        paymentRepository.saveAndFlush(pendingPayment(uniqueOrderId(), key));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(pendingPayment(uniqueOrderId(), key)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private HttpAnswer postPayment(String body) {
        return RestClient.create("http://localhost:" + port).post()
                .uri("/internal/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpAnswer(response.getStatusCode().value(), response.bodyTo(String.class)));
    }

    private List<Integer> statusesOf(List<Future<HttpAnswer>> requests) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (Future<HttpAnswer> request : requests) {
            statuses.add(request.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).status());
        }
        return statuses;
    }

    private List<Payment> paymentsOfTheOrder(Long orderId) {
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getOrderId().equals(orderId))
                .toList();
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

    private String paymentBody(Long orderId, String amount, String method, String key) {
        return """
                {"orderId": %d, "amount": %s, "paymentMethod": "%s", "idempotencyKey": "%s"}"""
                .formatted(orderId, amount, method, key);
    }

    private Long uniqueOrderId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }

    private String uniqueKey() {
        return UUID.randomUUID().toString();
    }
}
