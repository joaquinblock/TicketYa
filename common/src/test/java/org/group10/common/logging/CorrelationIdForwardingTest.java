package org.group10.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que sigue en la cadena (en el api-gateway, el request que se reenvía) ve el mismo id que se registra y se
 * devuelve, aunque el cliente no lo haya mandado.
 */
class CorrelationIdForwardingTest {

    private static final String HEADER_NAME = "X-Correlation-ID";

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
    void stopCapturing() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void doFilter_withoutHeader_theChainSeesTheGeneratedId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        HttpServletRequest seen = filterAndCapture(new CorrelationIdFilter(), new MockHttpServletRequest(), response);

        String generated = response.getHeader(HEADER_NAME);
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(seen.getHeader(HEADER_NAME)).isEqualTo(generated);
        assertThat(seen.getHeader(HEADER_NAME.toLowerCase())).isEqualTo(generated);
        assertThat(Collections.list(seen.getHeaders(HEADER_NAME))).containsExactly(generated);
        assertThat(Collections.list(seen.getHeaderNames())).filteredOn(HEADER_NAME::equalsIgnoreCase).hasSize(1);
    }

    @Test
    void doFilter_invalidHeader_theChainSeesTheNewIdInstead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, "no válido con espacios");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HttpServletRequest seen = filterAndCapture(new CorrelationIdFilter(), request, response);

        assertThat(Collections.list(seen.getHeaders(HEADER_NAME))).containsExactly(response.getHeader(HEADER_NAME));
    }

    @Test
    void doFilter_validHeader_theChainSeesItUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, "demo-compra-1");

        HttpServletRequest seen = filterAndCapture(new CorrelationIdFilter(), request, new MockHttpServletResponse());

        assertThat(seen.getHeader(HEADER_NAME)).isEqualTo("demo-compra-1");
    }

    @Test
    void doFilter_otherHeaders_passUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Queue-Token", "token");

        HttpServletRequest seen = filterAndCapture(new CorrelationIdFilter(), request, new MockHttpServletResponse());

        assertThat(seen.getHeader("X-Queue-Token")).isEqualTo("token");
        assertThat(Collections.list(seen.getHeaderNames())).contains("X-Queue-Token");
    }

    @Test
    void doFilter_customUser_logsTheUserItResolves() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.addHeader("X-User-Id", "3");
        request.setAttribute("user", "7");

        filterAndCapture(new CorrelationIdFilter(r -> (String) r.getAttribute("user")), request,
                new MockHttpServletResponse());

        assertThat(loggedUser()).isEqualTo("7");
    }

    @Test
    void doFilter_customUserResolvesNone_logsAnonymous() throws Exception {
        filterAndCapture(new CorrelationIdFilter(r -> null), new MockHttpServletRequest("GET", "/api/events"),
                new MockHttpServletResponse());

        assertThat(loggedUser()).isEqualTo("anonymous");
    }

    private Object loggedUser() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst().getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals("userId")).findFirst().orElseThrow().value;
    }

    private static HttpServletRequest filterAndCapture(CorrelationIdFilter filter, MockHttpServletRequest request,
                                                       MockHttpServletResponse response) throws Exception {
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                seen.set((HttpServletRequest) servletRequest));
        assertThat(seen.get()).isNotNull();
        return seen.get();
    }
}
