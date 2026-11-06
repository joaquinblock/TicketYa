package org.group10.event.config;

import org.group10.event.client.AuthClient;
import org.group10.event.client.TicketClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "auth-service", types = AuthClient.class)
@ImportHttpServices(group = "ticket-service", types = TicketClient.class)
public class HttpClientsConfig {
}
