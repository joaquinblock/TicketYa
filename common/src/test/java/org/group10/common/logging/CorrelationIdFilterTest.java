package org.group10.common.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private static final String HEADER_NAME = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";
    private static final String VALID_ID = "ticket-123.abc:xyz";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.remove(MDC_KEY);
    }

    @Test
    void doFilter_validHeader_usesAndClearsTheId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, VALID_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(MDC.get(MDC_KEY)).isEqualTo(VALID_ID));

        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void doFilter_missingOrInvalidHeader_generatesUuidAndClearsTheId(String id) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (id != null) {
            request.addHeader(HEADER_NAME, id);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(UUID.fromString(MDC.get(MDC_KEY))).isNotNull());

        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void doFilter_chainFails_clearsTheId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER_NAME, VALID_ID);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> {
                    throw new IOException("downstream failed");
                })).isInstanceOf(IOException.class);

        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    private static Stream<String> invalidIds() {
        return Stream.<String>of(null, "", " ", "x".repeat(129), "bad\nvalue");
    }
}
