package org.group10.event.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Retries a request when it never got an answer: the connection was refused or the read timeout ran out. Both
 * reach this interceptor as an {@link IOException}, before the {@code RestClient} turns it into a
 * {@code ResourceAccessException} (503).
 * <p>
 * Only idempotent methods are retried, because a POST that timed out may have been applied anyway. An error
 * response (4xx or 5xx) is an answer, so it is returned as is and never retried.
 */
@Slf4j
public class RetryOnIoErrorInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<HttpMethod> IDEMPOTENT = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private final int maxAttempts;
    private final Duration backoff;

    /**
     * @param maxAttempts total attempts, counting the first one; 1 means no retries
     * @param backoff     wait between attempts
     */
    public RetryOnIoErrorInterceptor(int maxAttempts, Duration backoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts tiene que ser al menos 1");
        }
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        int attempts = IDEMPOTENT.contains(request.getMethod()) ? maxAttempts : 1;
        for (int attempt = 1; ; attempt++) {
            try {
                return execution.execute(request, body);
            } catch (IOException e) {
                if (attempt >= attempts) {
                    throw e;
                }
                log.warn("{} {} falló ({}), reintento {}/{}", request.getMethod(), request.getURI(), e.getClass().getSimpleName(),
                        attempt, attempts - 1);
                pause();
            }
        }
    }

    private void pause() throws IOException {
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la espera entre reintentos", e);
        }
    }
}
