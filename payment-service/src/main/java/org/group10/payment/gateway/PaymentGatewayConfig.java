package org.group10.payment.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/**
 * Los servicios reciben la pasarela ya envuelta con timeout, reintentos y circuit breaker.
 */
@Configuration
@EnableConfigurationProperties(GatewayResilienceProperties.class)
public class PaymentGatewayConfig {

    @Bean
    @Primary
    public ResilientPaymentGateway resilientPaymentGateway(MockPaymentGateway mockPaymentGateway,
                                                           GatewayResilienceProperties properties) {
        return new ResilientPaymentGateway(mockPaymentGateway, properties, Clock.systemUTC());
    }
}
