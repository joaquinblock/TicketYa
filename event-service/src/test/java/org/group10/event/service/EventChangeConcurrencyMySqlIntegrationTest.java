package org.group10.event.service;

import org.group10.common.security.Caller;
import org.group10.event.client.TicketClient;
import org.group10.event.config.RabbitConfig;
import org.group10.event.dto.EventResumeRequest;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.messaging.EventChanged;
import org.group10.event.repository.EventRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
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
 * Runs against the MySQL of {@code compose.yaml}. {@link HoldChanges} keeps each change inside its transaction until
 * the other request gets there too (or 2 s go by), so without a lock both read the old status and both publish.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EventChangeConcurrencyMySqlIntegrationTest.HoldChanges.class)
class EventChangeConcurrencyMySqlIntegrationTest {

    private static final Caller ADMIN = new Caller(3L, "ADMIN", Set.of("EVENT_ADMIN"), null);

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private TicketClient ticketClient;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private HoldChanges holdChanges;

    private final List<Long> createdEventIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        holdChanges.reset();
    }

    @AfterEach
    void tearDown() {
        eventRepository.deleteAllById(createdEventIds);
    }

    @Test
    void cancel_twoRequestsAtTheSameTime_publishesOnce() throws Exception {
        long id = addEvent(EventStatus.PUBLISHED);

        List<Outcome> outcomes = atTheSameTime(() -> eventService.cancel(id, ADMIN));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(eventRepository.findById(id).orElseThrow().getStatus()).isEqualTo(EventStatus.CANCELLED);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitConfig.EVENTS_EXCHANGE), eq("event.cancelled"),
                any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    void suspend_twoRequestsAtTheSameTime_publishesOnce() throws Exception {
        long id = addEvent(EventStatus.PUBLISHED);

        List<Outcome> outcomes = atTheSameTime(() -> eventService.suspend(id, ADMIN));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        Event event = eventRepository.findById(id).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(EventStatus.SUSPENDED);
        assertThat(event.getStatusBeforeSuspension()).isEqualTo(EventStatus.PUBLISHED);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitConfig.EVENTS_EXCHANGE), eq("event.suspended"),
                any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    void resume_twoRequestsWithANewDateAtTheSameTime_onlyOneReschedules() throws Exception {
        long id = addEvent(EventStatus.SUSPENDED);
        LocalDateTime newDate = LocalDateTime.now().plusDays(90).withNano(0);

        List<Outcome> outcomes = atTheSameTime(() -> eventService.resume(id, ADMIN, new EventResumeRequest(newDate)));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(eventRepository.findById(id).orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitConfig.EVENTS_EXCHANGE), eq("event.rescheduled"),
                any(Object.class), any(MessagePostProcessor.class));
    }

    private List<Outcome> atTheSameTime(Callable<?> change) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Outcome>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        change.call();
                        return new Outcome(true);
                    } catch (RuntimeException rejected) {
                        return new Outcome(false);
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> attempt : attempts) {
                outcomes.add(attempt.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private long addEvent(EventStatus status) {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .name("Concurrencia " + System.nanoTime())
                .type(EventType.CONCERT)
                .organizationId(1L)
                .createdBy(2L)
                .venue(venueRepository.findAll().getFirst())
                .dateTime(LocalDateTime.now().plusDays(30).withNano(0))
                .status(status)
                .statusBeforeSuspension(status == EventStatus.SUSPENDED ? EventStatus.PUBLISHED : null)
                .highDemand(false)
                .build());
        createdEventIds.add(event.getId());
        return event.getId();
    }

    private record Outcome(boolean succeeded) {
    }

    @TestConfiguration
    static class HoldChanges {

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
