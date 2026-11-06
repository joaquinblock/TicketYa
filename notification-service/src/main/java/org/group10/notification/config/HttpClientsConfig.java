package org.group10.notification.config;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.EventClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "auth-service", types = AuthClient.class)
@ImportHttpServices(group = "event-service", types = EventClient.class)
public class HttpClientsConfig {
}
