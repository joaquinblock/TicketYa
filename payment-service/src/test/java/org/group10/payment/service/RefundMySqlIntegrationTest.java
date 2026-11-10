package org.group10.payment.service;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.entity.Refund;
import org.group10.payment.entity.RefundReason;
import org.group10.payment.entity.RefundStatus;
import org.group10.payment.gateway.PaymentGateway;
import org.group10.payment.gateway.RefundResult;
import org.group10.payment.repository.PaymentRepository;
import org.group10.payment.repository.RefundRepository;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RefundMySqlIntegrationTest {

    private static final int SIMULTANEOUS_REQUESTS = 8;
    private static final long TIMEOUT_SECONDS = 20;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    private record HttpAnswer(int status, String body) {
    }

    @Test
    void refund_returnRequestOnAnApprovedPayment_answers201AndStoresTheCreditedRefund() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        Long returnRequestId = uniqueId();

        HttpAnswer answer = postRefund(returnRequestBody(ticketId, payment.getOrderId(), "25.00", returnRequestId));

        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.body()).contains("\"status\":\"CREDITED\"", "\"reason\":\"RETURN_REQUEST\"");
        Refund refund = storedRefund(ticketId);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.CREDITED);
        assertThat(refund.getPayment().getId()).isEqualTo(payment.getId());
        assertThat(refund.getReturnRequestId()).isEqualTo(returnRequestId);
        assertThat(refund.getAmount()).isEqualByComparingTo("25.00");
        assertThat(refund.getGatewayId()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(value = RefundReason.class, names = {"EVENT_CANCELLED", "PURCHASE_COMPENSATION"})
    void refund_reasonWithoutReturnRequest_creditsTheTicketAndInformsTheReason(RefundReason reason) {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();

        HttpAnswer answer = postRefund(refundBody(ticketId, payment.getOrderId(), "25.00", reason));

        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.body()).contains("\"reason\":\"" + reason + "\"");
        assertThat(storedRefund(ticketId).getReturnRequestId()).isNull();
    }

    @Test
    void refund_twoTicketsOfTheSameOrder_areCreditedSeparately() {
        Payment payment = approvedPayment("100.00");
        Long firstTicket = uniqueId();
        Long secondTicket = uniqueId();

        postRefund(cancellationBody(firstTicket, payment.getOrderId(), "25.00"));
        HttpAnswer secondAnswer = postRefund(cancellationBody(secondTicket, payment.getOrderId(), "25.00"));

        assertThat(secondAnswer.status()).isEqualTo(201);
        assertThat(storedRefund(firstTicket).getGatewayId()).isNotEqualTo(storedRefund(secondTicket).getGatewayId());
    }

    @Test
    void refund_orderWithoutPayments_answers404WithoutCallingTheGateway() {
        HttpAnswer answer = postRefund(cancellationBody(uniqueId(), uniqueId(), "25.00"));

        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.body()).contains("payment_not_found");
        verify(paymentGateway, never()).refund(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "REJECTED"})
    void refund_orderWithoutAnApprovedPayment_answers409WithoutCallingTheGateway(PaymentStatus status) {
        Payment payment = paymentRepository.saveAndFlush(paymentInStatus(status, "100.00"));

        HttpAnswer answer = postRefund(cancellationBody(uniqueId(), payment.getOrderId(), "25.00"));

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.body()).contains("payment_not_refundable");
        verify(paymentGateway, never()).refund(any());
    }

    @Test
    void refund_gatewayDoesNotCredit_answers502AndStoresTheFailureWithItsReason() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        doReturn(RefundResult.failed("Cuenta cerrada")).when(paymentGateway).refund(any());

        HttpAnswer answer = postRefund(cancellationBody(ticketId, payment.getOrderId(), "25.00"));

        assertThat(answer.status()).isEqualTo(502);
        assertThat(answer.body()).contains("refund_failed", "Cuenta cerrada");
        assertThat(storedRefund(ticketId).getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(storedRefund(ticketId).getFailureReason()).isEqualTo("Cuenta cerrada");
    }

    @Test
    void refund_gatewayThrows_answers502AndStoresTheFailure() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        doThrow(new IllegalStateException("gateway down")).when(paymentGateway).refund(any());

        HttpAnswer answer = postRefund(cancellationBody(ticketId, payment.getOrderId(), "25.00"));

        assertThat(answer.status()).isEqualTo(502);
        assertThat(answer.body()).contains("refund_failed");
        assertThat(storedRefund(ticketId).getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void refund_oneTicketFails_doesNotAffectTheOtherTicketOfTheOrder() {
        Payment payment = approvedPayment("100.00");
        Long failedTicket = uniqueId();
        Long creditedTicket = uniqueId();
        doReturn(RefundResult.failed("Cuenta cerrada")).doCallRealMethod().when(paymentGateway).refund(any());

        postRefund(cancellationBody(failedTicket, payment.getOrderId(), "25.00"));
        HttpAnswer answer = postRefund(cancellationBody(creditedTicket, payment.getOrderId(), "25.00"));

        assertThat(answer.status()).isEqualTo(201);
        assertThat(storedRefund(failedTicket).getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(storedRefund(creditedTicket).getStatus()).isEqualTo(RefundStatus.CREDITED);
    }

    @Test
    void refund_failedRefundRetried_creditsTheSameRow() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        String body = cancellationBody(ticketId, payment.getOrderId(), "25.00");
        doReturn(RefundResult.failed("Cuenta cerrada")).doCallRealMethod().when(paymentGateway).refund(any());
        postRefund(body);
        Long failedRefundId = storedRefund(ticketId).getId();

        HttpAnswer retry = postRefund(body);

        assertThat(retry.status()).isEqualTo(201);
        Refund refund = storedRefund(ticketId);
        assertThat(refund.getId()).isEqualTo(failedRefundId);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.CREDITED);
        assertThat(refund.getFailureReason()).isNull();
    }

    @Test
    void refund_failedReturnRetriedByTheCancellation_updatesTheDataWithTheNewRequest() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        doReturn(RefundResult.failed("Cuenta cerrada")).doCallRealMethod().when(paymentGateway).refund(any());
        postRefund(returnRequestBody(ticketId, payment.getOrderId(), "25.00", uniqueId()));

        HttpAnswer answer = postRefund(cancellationBody(ticketId, payment.getOrderId(), "30.00"));

        assertThat(answer.status()).isEqualTo(201);
        Refund refund = storedRefund(ticketId);
        assertThat(refund.getReason()).isEqualTo(RefundReason.EVENT_CANCELLED);
        assertThat(refund.getReturnRequestId()).isNull();
        assertThat(refund.getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void refund_failedRefundRetriedAndFailsAgain_answers502AndStaysFailed() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        String body = cancellationBody(ticketId, payment.getOrderId(), "25.00");
        doReturn(RefundResult.failed("Cuenta cerrada")).when(paymentGateway).refund(any());
        postRefund(body);

        assertThat(postRefund(body).status()).isEqualTo(502);
        assertThat(storedRefund(ticketId).getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @Test
    void refund_sameDataRepeated_answers200WithTheStoredRefundWithoutCreditingAgain() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        Long storedReturnRequest = uniqueId();
        HttpAnswer firstAnswer = postRefund(returnRequestBody(ticketId, payment.getOrderId(), "25.00", storedReturnRequest));

        HttpAnswer repeatedAnswer = postRefund(returnRequestBody(ticketId, payment.getOrderId(), "25", storedReturnRequest));

        assertThat(repeatedAnswer.status()).isEqualTo(200);
        assertThat(repeatedAnswer.body()).isEqualTo(firstAnswer.body());
        verify(paymentGateway, times(1)).refund(any());
    }

    @Test
    void refund_otherDataOnACreditedTicket_answers409WithoutCreditingAgain() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        Long storedReturnRequest = uniqueId();
        postRefund(returnRequestBody(ticketId, payment.getOrderId(), "25.00", storedReturnRequest));

        List<HttpAnswer> conflicts = List.of(
                postRefund(returnRequestBody(ticketId, payment.getOrderId(), "25.01", storedReturnRequest)),
                postRefund(returnRequestBody(ticketId, payment.getOrderId(), "25.00", uniqueId())),
                postRefund(cancellationBody(ticketId, payment.getOrderId(), "25.00")),
                postRefund(returnRequestBody(ticketId, approvedPayment("100.00").getOrderId(), "25.00", storedReturnRequest)));

        assertThat(conflicts).allSatisfy(conflict -> {
            assertThat(conflict.status()).isEqualTo(409);
            assertThat(conflict.body()).contains("data_conflict");
        });
        verify(paymentGateway, times(1)).refund(any());
    }

    @Test
    void refund_storedRefundStillRequested_retriesTheGatewayAndCreditsIt() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        refundRepository.saveAndFlush(requestedRefund(ticketId, payment, "25.00"));

        HttpAnswer answer = postRefund(cancellationBody(ticketId, payment.getOrderId(), "25.00"));

        assertThat(answer.status()).isEqualTo(200);
        assertThat(storedRefund(ticketId).getStatus()).isEqualTo(RefundStatus.CREDITED);
    }

    @Test
    void refund_amountAboveTheCharge_answers409WithoutCallingTheGateway() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();

        HttpAnswer answer = postRefund(cancellationBody(ticketId, payment.getOrderId(), "100.01"));

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.body()).contains("payment_not_refundable");
        assertThat(refundRepository.findByTicketId(ticketId)).isEmpty();
        verify(paymentGateway, never()).refund(any());
    }

    @Test
    void refund_amountThatCompletesExactlyTheCharge_isCredited() {
        Payment payment = approvedPayment("100.00");
        postRefund(cancellationBody(uniqueId(), payment.getOrderId(), "60.00"));

        assertThat(postRefund(cancellationBody(uniqueId(), payment.getOrderId(), "40.00")).status()).isEqualTo(201);
        assertThat(postRefund(cancellationBody(uniqueId(), payment.getOrderId(), "0.01")).status()).isEqualTo(409);
    }

    @Test
    void refund_failedRefundsDoNotCountAgainstTheCharge() {
        Payment payment = approvedPayment("100.00");
        doReturn(RefundResult.failed("Cuenta cerrada")).doCallRealMethod().when(paymentGateway).refund(any());
        postRefund(cancellationBody(uniqueId(), payment.getOrderId(), "100.00"));

        assertThat(postRefund(cancellationBody(uniqueId(), payment.getOrderId(), "100.00")).status()).isEqualTo(201);
    }

    @Test
    void refund_failedRefundThatNoLongerFits_answers409AndStaysFailed() {
        Payment payment = approvedPayment("100.00");
        Long failedTicket = uniqueId();
        doReturn(RefundResult.failed("Cuenta cerrada")).doCallRealMethod().when(paymentGateway).refund(any());
        String failedBody = cancellationBody(failedTicket, payment.getOrderId(), "60.00");
        postRefund(failedBody);
        postRefund(cancellationBody(uniqueId(), payment.getOrderId(), "60.00"));

        HttpAnswer retry = postRefund(failedBody);

        assertThat(retry.status()).isEqualTo(409);
        assertThat(retry.body()).contains("payment_not_refundable");
        assertThat(storedRefund(failedTicket).getStatus()).isEqualTo(RefundStatus.FAILED);
    }

    @RepeatedTest(5)
    void refund_sameTicketSimultaneously_storesOneCreditedRefundAndAnswersOneCreation() throws Exception {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        String body = cancellationBody(ticketId, payment.getOrderId(), "100.00");

        List<HttpAnswer> answers = postSimultaneously(IntStream.range(0, SIMULTANEOUS_REQUESTS)
                .mapToObj(request -> body)
                .toList());

        assertThat(answers.stream().map(HttpAnswer::status).toList())
                .containsOnlyOnce(201)
                .allMatch(status -> status == 201 || status == 200);
        assertThat(answers).allSatisfy(answer -> assertThat(answer.body()).contains("\"status\":\"CREDITED\""));
        assertThat(answers.stream().map(HttpAnswer::body).distinct()).hasSize(1);
        assertThat(refundsOfThePayment(payment)).singleElement()
                .satisfies(refund -> assertThat(refund.getStatus()).isEqualTo(RefundStatus.CREDITED));
    }

    @RepeatedTest(5)
    void refund_differentTicketsSimultaneously_neverExceedsTheCharge() throws Exception {
        Payment payment = approvedPayment("100.00");

        List<HttpAnswer> answers = postSimultaneously(IntStream.range(0, SIMULTANEOUS_REQUESTS)
                .mapToObj(request -> cancellationBody(uniqueId(), payment.getOrderId(), "25.00"))
                .toList());

        List<Integer> statuses = answers.stream().map(HttpAnswer::status).toList();
        assertThat(statuses).filteredOn(status -> status == 201).hasSize(4);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(4);
        assertThat(refundsOfThePayment(payment))
                .hasSize(4)
                .allSatisfy(refund -> assertThat(refund.getStatus()).isEqualTo(RefundStatus.CREDITED));
    }

    @Test
    void save_secondRefundOfTheSameTicket_isRejectedByTheDatabase() {
        Payment payment = approvedPayment("100.00");
        Long ticketId = uniqueId();
        refundRepository.saveAndFlush(requestedRefund(ticketId, payment, "25.00"));

        assertThatThrownBy(() -> refundRepository.saveAndFlush(requestedRefund(ticketId, payment, "25.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_returnRequestReasonWithoutItsId_isRejectedByTheDatabase() {
        Payment payment = approvedPayment("100.00");
        Refund invalidRefund = Refund.builder()
                .ticketId(uniqueId())
                .payment(payment)
                .reason(RefundReason.RETURN_REQUEST)
                .amount(new BigDecimal("25.00"))
                .status(RefundStatus.REQUESTED)
                .build();

        assertThatThrownBy(() -> refundRepository.saveAndFlush(invalidRefund))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<HttpAnswer> postSimultaneously(List<String> bodies) throws Exception {
        CountDownLatch ready = new CountDownLatch(bodies.size());
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(bodies.size())) {
            List<Future<HttpAnswer>> requests = bodies.stream()
                    .map(body -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return postRefund(body);
                    }))
                    .toList();

            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return answersOf(requests);
        }
    }

    private List<HttpAnswer> answersOf(List<Future<HttpAnswer>> requests) throws Exception {
        List<HttpAnswer> answers = new ArrayList<>();
        for (Future<HttpAnswer> request : requests) {
            answers.add(request.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        return answers;
    }

    private HttpAnswer postRefund(String body) {
        return RestClient.create("http://localhost:" + port).post()
                .uri("/internal/refunds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new HttpAnswer(response.getStatusCode().value(), response.bodyTo(String.class)));
    }

    private Refund storedRefund(Long ticketId) {
        return refundRepository.findByTicketId(ticketId).orElseThrow();
    }

    private List<Refund> refundsOfThePayment(Payment payment) {
        return refundRepository.findAll().stream()
                .filter(refund -> refund.getPayment().getId().equals(payment.getId()))
                .toList();
    }

    private Payment approvedPayment(String amount) {
        return paymentRepository.saveAndFlush(paymentInStatus(PaymentStatus.APPROVED, amount));
    }

    private Payment paymentInStatus(PaymentStatus status, String amount) {
        return Payment.builder()
                .orderId(uniqueId())
                .idempotencyKey(UUID.randomUUID().toString())
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .amount(new BigDecimal(amount))
                .status(status)
                .build();
    }

    private Refund requestedRefund(Long ticketId, Payment payment, String amount) {
        return Refund.builder()
                .ticketId(ticketId)
                .payment(payment)
                .reason(RefundReason.EVENT_CANCELLED)
                .amount(new BigDecimal(amount))
                .status(RefundStatus.REQUESTED)
                .build();
    }

    private String returnRequestBody(Long ticketId, Long orderId, String amount, Long returnRequestId) {
        return """
                {"ticketId": %d, "orderId": %d, "amount": %s, "reason": "RETURN_REQUEST", "returnRequestId": %d}"""
                .formatted(ticketId, orderId, amount, returnRequestId);
    }

    private String cancellationBody(Long ticketId, Long orderId, String amount) {
        return refundBody(ticketId, orderId, amount, RefundReason.EVENT_CANCELLED);
    }

    private String refundBody(Long ticketId, Long orderId, String amount, RefundReason reason) {
        return """
                {"ticketId": %d, "orderId": %d, "amount": %s, "reason": "%s"}"""
                .formatted(ticketId, orderId, amount, reason);
    }

    private Long uniqueId() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE);
    }
}
