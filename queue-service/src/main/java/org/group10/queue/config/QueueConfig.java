package org.group10.queue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.group10.queue.security.QueueJwtProperties;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({QueueProperties.class, QueueJwtProperties.class})
public class QueueConfig {

    @Bean
    Clock queueClock() {
        return Clock.systemDefaultZone();
    }
}
