package org.group10.recommendation.config;

import org.group10.recommendation.client.EventClient;
import org.group10.recommendation.client.TicketClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "event-service", types = EventClient.class)
@ImportHttpServices(group = "ticket-service", types = TicketClient.class)
public class HttpClientsConfig {
}
