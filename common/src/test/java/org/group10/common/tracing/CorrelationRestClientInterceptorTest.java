package org.group10.common.tracing;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationRestClientInterceptorTest {

    @Test
    void interceptWithCorrelationId_propagatesHeader() throws Exception {
        MDC.put(CorrelationHeaders.MDC_KEY, "operation-123");
        CorrelationRestClientInterceptor interceptor = new CorrelationRestClientInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest();

        interceptor.intercept(request, new byte[0], (httpRequest, body) -> null);

        assertThat(request.getHeaders().getFirst(CorrelationHeaders.HEADER)).isEqualTo("operation-123");
        MDC.clear();
    }
}
