package org.group10.event.config;

import org.group10.event.client.AuthClient;
import org.group10.event.client.RetryOnIoErrorInterceptor;
import org.group10.event.client.TicketClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import java.time.Duration;

/**
 * Registers the HTTP clients. Timeouts are set per group in {@code spring.http.serviceclient.<group>} of the
 * {@code application.yml}; retries are added here, the same for every group.
 */
@Configuration
@ImportHttpServices(group = "auth-service", types = AuthClient.class)
@ImportHttpServices(group = "ticket-service", types = TicketClient.class)
public class HttpClientsConfig {

    @Bean
    RestClientHttpServiceGroupConfigurer retryConfigurer(
            @Value("${ticketya.http.retry.max-attempts:2}") int maxAttempts,
            @Value("${ticketya.http.retry.backoff:200ms}") Duration backoff) {
        RetryOnIoErrorInterceptor retry = new RetryOnIoErrorInterceptor(maxAttempts, backoff);
        return groups -> groups.forEachClient((group, builder) -> builder.requestInterceptor(retry));
    }
}
