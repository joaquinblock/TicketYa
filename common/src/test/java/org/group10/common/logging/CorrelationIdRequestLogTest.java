package org.group10.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CorrelationIdRequestLogTest {

    private static final String HEADER_NAME = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";
    private static final String VALID_ID = "operation-123";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureFilterLogs() {
        logger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void stopCapturingAndClearMdc() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.remove(MDC_KEY);
    }

    @Test
    void doFilter_validHeader_returnsTheSameIdInTheResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(HEADER_NAME, VALID_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getHeader(HEADER_NAME)).isEqualTo(VALID_ID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "bad\nvalue"})
    void doFilter_missingOrInvalidHeader_returnsANewUuidInsteadOfTheHeader(String id) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        if (id != null) {
            request.addHeader(HEADER_NAME, id);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(UUID.fromString(response.getHeader(HEADER_NAME))).isNotNull();
    }

    @Test
    void doFilter_requestOfAUser_logsEndpointUserStatusAndDurationWithItsCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(HEADER_NAME, VALID_ID);
        request.addHeader("X-User-Id", "7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> ((MockHttpServletResponse) servletResponse).setStatus(409));

        ILoggingEvent event = theOnlyRequestLog();
        assertThat(fieldsOf(event))
                .containsEntry("method", "POST")
                .containsEntry("endpoint", "/api/orders")
                .containsEntry("userId", "7")
                .containsEntry("status", 409)
                .containsKey("durationMs");
        assertThat(event.getMDCPropertyMap()).containsEntry(MDC_KEY, VALID_ID);
    }

    @Test
    void doFilter_requestWithoutUser_logsAnonymous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> { });

        assertThat(fieldsOf(theOnlyRequestLog())).containsEntry("userId", "anonymous");
    }

    @Test
    void currentOrNew_withoutRequestContext_doesNotLeaveAnIdInTheThread() {
        String generated = CorrelationIds.currentOrNew();

        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void callFromAJobThread_carriesAnIdAndDoesNotLeaveItInTheThread() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment-service")
                .requestInterceptor(new CorrelationIdRequestInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://payment-service/internal/refunds"))
                .andExpect(request -> assertThat(request.getHeaders().getFirst(HEADER_NAME)).isNotBlank())
                .andRespond(withSuccess());

        builder.build().post().uri("/internal/refunds").retrieve().toBodilessEntity();

        server.verify();
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    private ILoggingEvent theOnlyRequestLog() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst();
    }

    private Map<String, Object> fieldsOf(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value, (first, second) -> second));
    }
}
