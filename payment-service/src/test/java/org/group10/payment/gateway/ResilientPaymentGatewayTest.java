package org.group10.payment.gateway;

import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentMethod;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.entity.Refund;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientPaymentGatewayTest {

    private static final Duration TIMEOUT = Duration.ofMillis(100);
    private static final int MAX_ATTEMPTS = 3;
    private static final int MINIMUM_CALLS = 5;
    private static final Duration WAIT_IN_OPEN_STATE = Duration.ofSeconds(10);
    private static final int CALLS_IN_HALF_OPEN = 2;

    private static final GatewayResilienceProperties PROPERTIES = new GatewayResilienceProperties(
            TIMEOUT, MAX_ATTEMPTS, Duration.ofMillis(1),
            10, MINIMUM_CALLS, 50, WAIT_IN_OPEN_STATE, CALLS_IN_HALF_OPEN);

    private static final ChargeResult APPROVED = ChargeResult.approved("MOCK-1");
    private static final Supplier<ChargeResult> ANSWERS_APPROVED = () -> APPROVED;
    private static final Supplier<ChargeResult> FAILS = () -> {
        throw new PaymentGatewayUnavailableException("caída");
    };

    private final Payment payment = Payment.builder()
            .orderId(1L)
            .idempotencyKey("attempt-1")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .amount(new BigDecimal("1000.00"))
            .status(PaymentStatus.PENDING)
            .build();

    private final MutableClock clock = new MutableClock();
    private final ScriptedGateway delegate = new ScriptedGateway();
    private final ResilientPaymentGateway gateway = new ResilientPaymentGateway(delegate, PROPERTIES, clock);

    @AfterEach
    void closeGateway() {
        gateway.close();
    }

    @Test
    void charge_gatewayAnswers_returnsItsResultWithASingleCall() {
        delegate.script(ANSWERS_APPROVED);

        assertThat(gateway.charge(payment)).isEqualTo(APPROVED);
        assertThat(delegate.charges()).isEqualTo(1);
    }

    @Test
    void charge_gatewayFailsOnceThenAnswers_returnsTheResultChargedOnce() {
        delegate.script(FAILS, ANSWERS_APPROVED);

        assertThat(gateway.charge(payment)).isEqualTo(APPROVED);
        assertThat(delegate.charges()).isEqualTo(2);
    }

    @Test
    void charge_gatewayFailsOnEveryAttempt_throwsUnavailableAfterTheMaxAttempts() {
        delegate.script(FAILS);

        assertThatThrownBy(() -> gateway.charge(payment))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(delegate.charges()).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    void charge_gatewayTooSlow_cutsEachAttemptByTimeoutAndThrowsUnavailable() {
        CountDownLatch neverReleased = new CountDownLatch(1);
        delegate.script(() -> {
            try {
                neverReleased.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return APPROVED;
        });

        long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.charge(payment))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        assertThat(delegate.charges()).isEqualTo(MAX_ATTEMPTS);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void charge_gatewaySlowButWithinTheTimeout_returnsTheResult() {
        delegate.script(() -> {
            sleepQuietly(Duration.ofMillis(20));
            return APPROVED;
        });

        assertThat(gateway.charge(payment)).isEqualTo(APPROVED);
        assertThat(delegate.charges()).isEqualTo(1);
    }

    @Test
    void charge_gatewayRejects_doesNotRetry() {
        ChargeResult rejected = ChargeResult.rejected("MOCK-2", "Fondos insuficientes");
        delegate.script(() -> rejected);

        assertThat(gateway.charge(payment)).isEqualTo(rejected);
        assertThat(delegate.charges()).isEqualTo(1);
    }

    @Test
    void charge_unexpectedError_isNotRetried() {
        delegate.script(() -> {
            throw new IllegalStateException("bug del adaptador");
        });

        assertThatThrownBy(() -> gateway.charge(payment)).isInstanceOf(IllegalStateException.class);
        assertThat(delegate.charges()).isEqualTo(1);
    }

    @Test
    void charge_recurrentFailures_opensTheCircuitAndAnswersWithoutCallingTheGateway() {
        delegate.script(FAILS);
        failTheMinimumCallsToOpenTheCircuit();
        int chargesWhenOpened = delegate.charges();

        assertThatThrownBy(() -> gateway.charge(payment))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(delegate.charges()).isEqualTo(chargesWhenOpened);
    }

    @Test
    void charge_gatewayRecoversAfterTheOpenWait_successfulProbesCloseTheCircuit() {
        delegate.script(FAILS);
        failTheMinimumCallsToOpenTheCircuit();

        delegate.script(ANSWERS_APPROVED);
        clock.advance(WAIT_IN_OPEN_STATE.plusMillis(1));

        for (int probe = 0; probe < CALLS_IN_HALF_OPEN; probe++) {
            assertThat(gateway.charge(payment)).isEqualTo(APPROVED);
        }
        assertThat(gateway.charge(payment)).isEqualTo(APPROVED);
        assertThat(delegate.charges()).isEqualTo(MINIMUM_CALLS + CALLS_IN_HALF_OPEN + 1);
    }

    @Test
    void charge_circuitStillOpenBeforeTheWaitEnds_doesNotCallTheGateway() {
        delegate.script(FAILS);
        failTheMinimumCallsToOpenTheCircuit();

        delegate.script(ANSWERS_APPROVED);
        clock.advance(WAIT_IN_OPEN_STATE.minusSeconds(1));

        assertThatThrownBy(() -> gateway.charge(payment))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(delegate.charges()).isEqualTo(MINIMUM_CALLS);
    }

    @Test
    void refund_gatewayFailsOnceAndAnswersOnTheRetry_creditsOnceWithTheSameRefund() {
        Refund refund = Refund.builder().ticketId(5L).build();
        delegate.scriptRefund(
                () -> { throw new PaymentGatewayUnavailableException("caída momentánea"); },
                () -> RefundResult.credited("MOCK-R"));

        assertThat(gateway.refund(refund)).isEqualTo(RefundResult.credited("MOCK-R"));
        assertThat(delegate.refunds()).isEqualTo(2);
    }

    @Test
    void refund_gatewayFailsEveryAttempt_exhaustsTheRetries() {
        Refund refund = Refund.builder().ticketId(5L).build();
        delegate.failRefunds();

        assertThatThrownBy(() -> gateway.refund(refund))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        assertThat(delegate.refunds()).isEqualTo(MAX_ATTEMPTS);
    }

    private void failTheMinimumCallsToOpenTheCircuit() {
        int requests = (MINIMUM_CALLS + MAX_ATTEMPTS - 1) / MAX_ATTEMPTS;
        for (int request = 0; request < requests; request++) {
            assertThatThrownBy(() -> gateway.charge(payment))
                    .isInstanceOf(PaymentGatewayUnavailableException.class);
        }
        assertThat(delegate.charges()).isEqualTo(MINIMUM_CALLS);
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ScriptedGateway implements PaymentGateway {

        private final Deque<Supplier<ChargeResult>> answers = new ArrayDeque<>();
        private final Deque<Supplier<RefundResult>> refundAnswers = new ArrayDeque<>();
        private final AtomicInteger charges = new AtomicInteger();
        private final AtomicInteger refunds = new AtomicInteger();
        private volatile boolean failRefunds;

        @SafeVarargs
        final void script(Supplier<ChargeResult>... nextAnswers) {
            synchronized (answers) {
                answers.clear();
                answers.addAll(List.of(nextAnswers));
            }
        }

        void failRefunds() {
            failRefunds = true;
        }

        @SafeVarargs
        final void scriptRefund(Supplier<RefundResult>... nextAnswers) {
            synchronized (refundAnswers) {
                refundAnswers.clear();
                refundAnswers.addAll(List.of(nextAnswers));
            }
        }

        int charges() {
            return charges.get();
        }

        int refunds() {
            return refunds.get();
        }

        @Override
        public ChargeResult charge(Payment payment) {
            charges.incrementAndGet();
            Supplier<ChargeResult> answer;
            synchronized (answers) {
                answer = answers.size() > 1 ? answers.poll() : answers.peek();
            }
            return answer.get();
        }

        @Override
        public RefundResult refund(Refund refund) {
            refunds.incrementAndGet();
            if (failRefunds) {
                throw new PaymentGatewayUnavailableException("caída");
            }
            synchronized (refundAnswers) {
                Supplier<RefundResult> answer =
                        refundAnswers.size() > 1 ? refundAnswers.poll() : refundAnswers.peek();
                return answer == null ? RefundResult.credited("MOCK-R") : answer.get();
            }
        }
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now = Instant.parse("2026-11-23T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
