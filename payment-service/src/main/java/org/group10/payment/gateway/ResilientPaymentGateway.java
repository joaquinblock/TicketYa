package org.group10.payment.gateway;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.group10.payment.entity.Payment;
import org.group10.payment.entity.PaymentStatus;
import org.group10.payment.entity.Refund;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Envuelve a la pasarela para que un problema momentáneo no frustre el cobro.
 * Cada intento tiene timeout y se reintenta con la misma clave de idempotencia,
 * así la pasarela nunca cobra dos veces. El circuit breaker cuenta cada intento:
 * ante fallas recurrentes se abre y responde al instante, sin esperar los reintentos.
 * Los reintegros usan los mismos límites y la misma clave derivada del ticket.
 */
@Slf4j
public class ResilientPaymentGateway implements PaymentGateway, AutoCloseable {

    private static final String NAME = "payment-gateway";

    private final PaymentGateway delegate;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ResilientPaymentGateway(PaymentGateway delegate, GatewayResilienceProperties properties, Clock clock) {
        this.delegate = delegate;
        this.timeLimiter = TimeLimiter.of(NAME, TimeLimiterConfig.custom()
                .timeoutDuration(properties.timeout())
                .cancelRunningFuture(true)
                .build());
        this.circuitBreaker = CircuitBreaker.of(NAME, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(properties.waitInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.callsInHalfOpenState())
                .recordExceptions(PaymentGatewayUnavailableException.class)
                .clock(clock)
                .build());
        this.retry = Retry.of(NAME, RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .waitDuration(properties.waitBetweenAttempts())
                .retryExceptions(PaymentGatewayUnavailableException.class)
                .build());

        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker de la pasarela: {}", event.getStateTransition()));
    }

    @Override
    public ChargeResult charge(Payment payment) {
        Supplier<ChargeResult> attempt = () -> callWithTimeout(() -> delegate.charge(payment));
        Supplier<ChargeResult> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, attempt);
        Supplier<ChargeResult> retried = Retry.decorateSupplier(retry, guarded);
        try {
            return retried.get();
        } catch (CallNotPermittedException openCircuit) {
            throw new PaymentGatewayUnavailableException("La pasarela viene fallando: el circuito está abierto");
        }
    }

    @Override
    public PaymentStatus cancel(Payment payment) {
        return delegate.cancel(payment);
    }

    @Override
    public RefundResult refund(Refund refund) {
        Supplier<RefundResult> attempt = () -> callWithTimeout(() -> delegate.refund(refund));
        Supplier<RefundResult> guarded = CircuitBreaker.decorateSupplier(circuitBreaker, attempt);
        Supplier<RefundResult> retried = Retry.decorateSupplier(retry, guarded);
        try {
            return retried.get();
        } catch (CallNotPermittedException openCircuit) {
            throw new PaymentGatewayUnavailableException("La pasarela viene fallando: el circuito está abierto");
        }
    }

    @Override
    public void close() {
        executor.close();
    }

    private <T> T callWithTimeout(Supplier<T> call) {
        try {
            return timeLimiter.executeFutureSupplier(() -> executor.submit(call::get));
        } catch (TimeoutException timeout) {
            throw new PaymentGatewayUnavailableException("La pasarela no respondió a tiempo");
        } catch (RuntimeException failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayUnavailableException("Se cortó la espera a la pasarela");
        } catch (Exception unexpected) {
            throw new PaymentGatewayUnavailableException("La llamada a la pasarela se cortó: " + unexpected);
        }
    }
}
