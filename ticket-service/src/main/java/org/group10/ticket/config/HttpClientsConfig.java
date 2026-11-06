package org.group10.ticket.config;

import org.group10.ticket.client.AuthClient;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.PaymentClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "event-service", types = EventClient.class)
@ImportHttpServices(group = "payment-service", types = PaymentClient.class)
@ImportHttpServices(group = "auth-service", types = AuthClient.class)
public class HttpClientsConfig {
}
