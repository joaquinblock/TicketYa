package org.group10.common.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TracingAutoConfigurationTest {

    private final ObservationPredicate predicate = new TracingAutoConfiguration().ticketyaIgnoreActuatorObservations();

    @Test
    void healthcheck_isNotTraced() {
        assertThat(predicate.test("http.server.requests", requestTo("/actuator/health"))).isFalse();
    }

    @Test
    void apiRequest_isTraced() {
        assertThat(predicate.test("http.server.requests", requestTo("/api/orders/42/pay"))).isTrue();
    }

    @Test
    void observationThatIsNotARequest_isTraced() {
        assertThat(predicate.test("tasks.scheduled.execution", new Observation.Context())).isTrue();
    }

    private static ServerRequestObservationContext requestTo(String uri) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", uri), new MockHttpServletResponse());
    }
}
