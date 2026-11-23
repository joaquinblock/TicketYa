package org.group10.recommendation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** En la zona de las fechas de los eventos, que event-service guarda sin zona. Como en event-service y ticket-service. */
    @Bean
    Clock clock(@Value("${ticketya.time-zone}") ZoneId timeZoneOfTheEvents) {
        return Clock.system(timeZoneOfTheEvents);
    }
}
