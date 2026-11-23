package org.group10.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CorrelationIdRequestInterceptorTest {

    private static final String HEADER_NAME = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";
    private static final String VALID_ID = "ticket-123.abc:xyz";

    @AfterEach
    void clearMdc() {
        MDC.remove(MDC_KEY);
    }

    @Test
    void callToPaymentService_carriesTheCurrentCorrelationIdForChargesAndRefunds() {
        MDC.put(MDC_KEY, VALID_ID);
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment-service")
                .requestInterceptor(new CorrelationIdRequestInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://payment-service/internal/payments"))
                .andExpect(request -> assertThat(request.getHeaders().getFirst(HEADER_NAME)).isEqualTo(VALID_ID))
                .andRespond(withSuccess());
        server.expect(requestTo("http://payment-service/internal/refunds"))
                .andExpect(request -> assertThat(request.getHeaders().getFirst(HEADER_NAME)).isEqualTo(VALID_ID))
                .andRespond(withSuccess());

        RestClient client = builder.build();
        client.post().uri("/internal/payments").retrieve().toBodilessEntity();
        client.post().uri("/internal/refunds").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void callWithoutRequestContext_generatesACorrelationId() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment-service")
                .requestInterceptor(new CorrelationIdRequestInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://payment-service/internal/payments"))
                .andExpect(request -> assertThat(UUID.fromString(
                        request.getHeaders().getFirst(HEADER_NAME))).isNotNull())
                .andRespond(withSuccess());

        builder.build().post().uri("/internal/payments").retrieve().toBodilessEntity();

        server.verify();
    }
}
