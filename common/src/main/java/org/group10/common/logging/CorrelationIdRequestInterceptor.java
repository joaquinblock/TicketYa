package org.group10.common.logging;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class CorrelationIdRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String previousId = MDC.get(CorrelationIds.MDC_KEY);
        String correlationId = CorrelationIds.validOrNew(previousId);
        request.getHeaders().set(CorrelationIds.HEADER_NAME, correlationId);
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        try {
            return execution.execute(request, body);
        } finally {
            if (previousId == null) {
                MDC.remove(CorrelationIds.MDC_KEY);
            } else {
                MDC.put(CorrelationIds.MDC_KEY, previousId);
            }
        }
    }
}
