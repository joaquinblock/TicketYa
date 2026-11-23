package org.group10.common.tracing;

import org.group10.common.security.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationFilterTest {

    private final CorrelationFilter filter = new CorrelationFilter();

    @Test
    void doFilterMissingCorrelationId_generatesIdAndLogsResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationHeaders.HEADER)).isNotBlank();
    }

    @Test
    void doFilterCorrelationId_preservesIdAndUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(CorrelationHeaders.HEADER, "operation-123");
        request.addHeader(GatewayHeaders.USER_ID, "7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationHeaders.HEADER)).isEqualTo("operation-123");
    }
}
