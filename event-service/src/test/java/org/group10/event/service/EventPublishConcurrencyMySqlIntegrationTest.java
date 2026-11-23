package org.group10.event.service;

import org.group10.common.security.Caller;
import org.group10.event.client.TicketClient;
import org.group10.event.config.RabbitConfig;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;
import org.group10.event.messaging.EventChanged;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.SectorRepository;
import org.group10.event.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Contra el MySQL de {@code compose.yaml}. {@link HoldPublication} deja cada publicación dentro de su transacción
 * hasta que llega la otra (o pasan 2 s): sin lock, las dos leen el borrador y las dos avisan, y los fans recibirían
 * dos mails (US-NOT-03).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EventPublishConcurrencyMySqlIntegrationTest.HoldPublication.class)
class EventPublishConcurrencyMySqlIntegrationTest {

    private static final Caller ORGANIZER = new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE"), 1L);

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private TicketClient ticketClient;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventSectorRepository eventSectorRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private HoldPublication holdPublication;

    private Long eventId;

    @BeforeEach
    void setUp() {
        holdPublication.reset();
    }

    @AfterEach
    void tearDown() {
        if (eventId != null) {
            eventSectorRepository.deleteAll(eventSectorRepository.findByEventId(eventId));
            eventRepository.deleteById(eventId);
        }
    }

    @Test
    void publish_twoRequestsAtTheSameTime_publishesOnce() throws Exception {
        eventId = addReadyDraft();

        List<Boolean> succeeded = atTheSameTime(() -> eventService.publish(eventId, ORGANIZER));

        assertThat(succeeded).containsExactlyInAnyOrder(true, false);
        assertThat(eventRepository.findById(eventId).orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitConfig.EVENTS_EXCHANGE), eq("event.published"),
                any(Object.class), any(MessagePostProcessor.class));
    }

    private List<Boolean> atTheSameTime(Runnable publication) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        publication.run();
                        return true;
                    } catch (RuntimeException rejected) {
                        return false;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> outcomes = new ArrayList<>();
            for (Future<Boolean> attempt : attempts) {
                outcomes.add(attempt.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    /** Un borrador listo para publicar: un sector sin numerar con precio y fecha futura. */
    private long addReadyDraft() {
        Venue venue = venueRepository.findAll().getFirst();
        Sector sector = sectorRepository.findByVenueIdOrderByNameAsc(venue.getId()).stream()
                .filter(s -> !s.isNumbered())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El lugar " + venue.getId() + " no tiene sectores sin numerar"));
        Event event = eventRepository.saveAndFlush(Event.builder()
                .name("Publicación concurrente " + System.nanoTime())
                .type(EventType.CONCERT)
                .organizationId(1L)
                .createdBy(2L)
                .venue(venue)
                .dateTime(LocalDateTime.now().plusDays(30).withNano(0))
                .status(EventStatus.DRAFT)
                .highDemand(false)
                .build());
        eventSectorRepository.saveAndFlush(EventSector.builder().event(event).sector(sector)
                .price(new BigDecimal("10000")).build());
        return event.getId();
    }

    @TestConfiguration
    static class HoldPublication {

        private volatile CyclicBarrier bothArrived = new CyclicBarrier(2);

        void reset() {
            bothArrived = new CyclicBarrier(2);
        }

        @EventListener
        void holdInsideTheTransaction(EventChanged changed) throws InterruptedException {
            try {
                bothArrived.await(2, TimeUnit.SECONDS);
            } catch (TimeoutException | BrokenBarrierException otherRequestWaitsForTheLock) {
            }
        }
    }
}
