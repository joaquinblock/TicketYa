package org.group10.payment.service;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.repository.PaymentRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.Mockito.doThrow;
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
    void charge_sameKeySimultaneously_storesOnePaymentAndEveryAnswerIsTheSame() throws Exception {
        Long orderId = uniqueOrderId();
        String body = paymentBody(orderId, "25000.00", "CREDIT_CARD", uniqueKey());

        List<HttpAnswer> answers = postSimultaneously(IntStream.range(0, SIMULTANEOUS_REQUESTS)
                .mapToObj(request -> body)
                .toList());

        assertThat(answers).allSatisfy(answer -> assertThat(answer.status()).isEqualTo(201));
        assertThat(answers.stream().distinct()).hasSize(1);
        assertThat(paymentsOfTheOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED));
    }

    @RepeatedTest(5)
    void charge_otherKeysSimultaneouslyOnTheSameOrder_chargesOnlyOneAttempt() throws Exception {
        Long orderId = uniqueOrderId();

        List<HttpAnswer> answers = postSimultaneously(IntStream.range(0, SIMULTANEOUS_REQUESTS)
                .mapToObj(request -> paymentBody(orderId, "25000.00", "CREDIT_CARD", uniqueKey()))
                .toList());

        assertThat(answers.stream().map(HttpAnswer::status).toList())
                .containsOnlyOnce(201)
                .allMatch(status -> status == 201 || status == 409);
        assertThat(paymentsOfTheOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED));
        verify(paymentGateway, times(1)).charge(any());
    }

    @Test
    void charge_sameKeyWhileTheFirstRequestIsAtTheGateway_retriesWithTheSameKeyAndBothGetTheSameAnswer() throws Exception {
        Long orderId = uniqueOrderId();
        String body = paymentBody(orderId, "25000.00", "CREDIT_CARD", uniqueKey());
        CountDownLatch gatewayReached = new CountDownLatch(1);
        CountDownLatch gatewayReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            gatewayReached.countDown();
            assertThat(gatewayReleased.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).doCallRealMethod().when(paymentGateway).charge(any());

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<HttpAnswer> firstRequest = executor.submit(() -> postPayment(body));
            assertThat(gatewayReached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            HttpAnswer whileCharging = postPayment(body);
            gatewayReleased.countDown();
            HttpAnswer firstAnswer = firstRequest.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(whileCharging.status()).isEqualTo(201);
            assertThat(firstAnswer).isEqualTo(whileCharging);
            assertThat(postPayment(body)).isEqualTo(firstAnswer);
        }

        assertThat(paymentsOfTheOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED));
        verify(paymentGateway, times(2)).charge(any());
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
    void charge_storedPendingPayment_chargesItAndLaterRetriesRepeatTheResult() {
        Long orderId = uniqueOrderId();
        String key = uniqueKey();
        paymentRepository.saveAndFlush(pendingPayment(orderId, key));
        String body = paymentBody(orderId, "25000.00", "CREDIT_CARD", key);

        HttpAnswer answer = postPayment(body);

        assertThat(answer.status()).isEqualTo(201);
        assertThat(postPayment(body)).isEqualTo(answer);
        assertThat(paymentsOfTheOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED));
        verify(paymentGateway, times(1)).charge(any());
    }

    @Test
    void charge_gatewayFailsAndThenRecovers_answers503AndTheRetryWithTheSameKeyCharges() {
        Long orderId = uniqueOrderId();
        String body = paymentBody(orderId, "25000.00", "CREDIT_CARD", uniqueKey());
        doThrow(new IllegalStateException("gateway down")).doCallRealMethod().when(paymentGateway).charge(any());

        HttpAnswer whileDown = postPayment(body);

        assertThat(whileDown.status()).isEqualTo(503);
        assertThat(whileDown.body()).contains("payment_gateway_unavailable");
        assertThat(paymentsOfTheOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING));

        assertThat(postPayment(body).status()).isEqualTo(201);
        assertThat(paymentsOfTheOrder(orderId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED));
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

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "APPROVED"})
    void save_secondActivePaymentOfTheOrder_isRejectedByTheDatabase(PaymentStatus activeStatus) {
        Long orderId = uniqueOrderId();
        paymentRepository.saveAndFlush(paymentInStatus(orderId, uniqueKey(), activeStatus));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(pendingPayment(orderId, uniqueKey())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_rejectedPayment_doesNotBlockANewAttemptOfTheOrder() {
        Long orderId = uniqueOrderId();
        paymentRepository.saveAndFlush(paymentInStatus(orderId, uniqueKey(), PaymentStatus.REJECTED));

        paymentRepository.saveAndFlush(pendingPayment(orderId, uniqueKey()));

        assertThat(paymentsOfTheOrder(orderId)).hasSize(2);
    }

    private List<HttpAnswer> postSimultaneously(List<String> bodies) throws Exception {
        CountDownLatch ready = new CountDownLatch(bodies.size());
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(bodies.size())) {
            List<Future<HttpAnswer>> requests = bodies.stream()
                    .map(body -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return postPayment(body);
                    }))
                    .toList();

            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return answersOf(requests);
        }
    }

    private HttpAnswer postPayment(String body) {
        return RestClient.create("http://localhost:" + port).post()
                .uri("/internal/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpAnswer(response.getStatusCode().value(), response.bodyTo(String.class)));
    }

    private List<HttpAnswer> answersOf(List<Future<HttpAnswer>> requests) throws Exception {
        List<HttpAnswer> answers = new ArrayList<>();
        for (Future<HttpAnswer> request : requests) {
            answers.add(request.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        return answers;
    }

    private List<Payment> paymentsOfTheOrder(Long orderId) {
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getOrderId().equals(orderId))
                .toList();
    }

    private Payment pendingPayment(Long orderId, String key) {
        return paymentInStatus(orderId, key, PaymentStatus.PENDING);
    }

    private Payment paymentInStatus(Long orderId, String key, PaymentStatus status) {
        return Payment.builder()
                .orderId(orderId)
                .idempotencyKey(key)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .amount(new BigDecimal("25000.00"))
                .status(status)
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
