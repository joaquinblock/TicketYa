package org.group10.event.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    Clock clock(@Value("${ticketya.time-zone}") ZoneId timeZoneOfTheEvents) {
        return Clock.system(timeZoneOfTheEvents);
    }
}
