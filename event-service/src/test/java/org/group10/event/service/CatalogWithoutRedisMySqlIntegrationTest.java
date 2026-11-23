package org.group10.event.service;

import org.group10.event.client.TicketClient;
import org.group10.event.dto.EventDetailResponse;
import org.group10.event.dto.EventSearchRequest;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Redis points to a port where nothing listens: the catalog has to answer from the MySQL of {@code compose.yaml},
 * and a change has to go through although it can't evict anything.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(CatalogWithoutRedisMySqlIntegrationTest.UnreachableRedis.class)
class CatalogWithoutRedisMySqlIntegrationTest {

    private static final EventSearchRequest INITIAL_LIST =
            new EventSearchRequest(null, null, null, null, null, null, null, null, null, null, null);

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private TicketClient ticketClient;

    @Autowired
    private EventService eventService;
    @Autowired
    private EventSearchService eventSearchService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private EventRepository eventRepository;

    @Test
    void reads_redisDown_answerFromTheDatabase() {
        Event published = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED).findFirst().orElseThrow();

        EventDetailResponse detail = eventService.findById(published.getId(), Optional.empty());

        assertThat(detail.name()).isEqualTo(published.getName());
        assertThat(eventSearchService.search(INITIAL_LIST).content()).isNotEmpty();
        assertThat(venueService.findById(detail.venue().id()).name()).isEqualTo(detail.venue().name());
    }

    @Test
    void markSoldOut_redisDown_changesTheStatusAnyway() {
        Event published = eventRepository.findAll().stream()
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED).findFirst().orElseThrow();

        try {
            assertThatCode(() -> eventService.markSoldOut(published.getId())).doesNotThrowAnyException();
            assertThat(eventRepository.findById(published.getId()).orElseThrow().getStatus())
                    .isEqualTo(EventStatus.SOLD_OUT);
        } finally {
            eventService.markBackOnSale(published.getId());
        }
    }

    @TestConfiguration
    static class UnreachableRedis {

        @Bean
        @Primary
        LettuceConnectionFactory unreachableRedis() {
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1),
                    LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build());
        }
    }
}
