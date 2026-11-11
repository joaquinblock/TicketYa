package org.group10.ticket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ReservationProperties.class)
public class ReservationConfig {

    @Bean
    Clock clock(@Value("${ticketya.time-zone}") ZoneId timeZoneOfTheEvents) {
        return Clock.system(timeZoneOfTheEvents);
    }
}
