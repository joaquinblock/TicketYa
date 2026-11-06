package org.group10.queue.config;

import org.group10.queue.client.EventClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "event-service", types = EventClient.class)
public class HttpClientsConfig {
}
