package org.group10.queue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class QueueConfig {

    @Bean
    Clock queueClock() {
        return Clock.systemDefaultZone();
    }
}
