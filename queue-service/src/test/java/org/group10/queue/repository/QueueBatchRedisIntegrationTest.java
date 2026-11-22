package org.group10.queue.repository;

import org.group10.queue.entity.QueueEntrySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueBatchRedisIntegrationTest {

    private static final int BATCH_SIZE = 100;
    private static final Duration INTERVAL = Duration.ofSeconds(30);
    private static final Duration ENABLED_WINDOW = Duration.ofMinutes(10);

    @Autowired
    private QueueRepository queueRepository;

    @Test
    void advanceDue_enablesNextHundredAndMovesWaitingPosition() {
        Long eventId = nextId();
        Instant firstArrival = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        List<Long> users = addWaitingCustomers(eventId, firstArrival, BATCH_SIZE + 1);
        Instant dueAt = firstArrival.plus(INTERVAL);

        assertThat(queueRepository.findCurrent(eventId, users.get(BATCH_SIZE), dueAt).orElseThrow().position())
                .isEqualTo(101);
        assertThat(queueRepository.advanceDue(eventId, dueAt, BATCH_SIZE, INTERVAL, ENABLED_WINDOW))
                .isEqualTo(BATCH_SIZE);

        QueueEntrySnapshot first = queueRepository.findCurrent(eventId, users.getFirst(), dueAt).orElseThrow();
        QueueEntrySnapshot lastEnabled = queueRepository.findCurrent(eventId, users.get(BATCH_SIZE - 1), dueAt)
                .orElseThrow();
        QueueEntrySnapshot next = queueRepository.findCurrent(eventId, users.get(BATCH_SIZE), dueAt).orElseThrow();

        assertThat(first.status()).isEqualTo("ENABLED");
        assertThat(lastEnabled.status()).isEqualTo("ENABLED");
        assertThat(first.enabledUntilEpochMillis())
                .isEqualTo(dueAt.plus(ENABLED_WINDOW).toEpochMilli());
        assertThat(next.status()).isEqualTo("WAITING");
        assertThat(next.position()).isEqualTo(1);
    }

    @Test
    void advanceDue_concurrentInstancesEnableOnlyOneBatchPerInterval() throws Exception {
        Long eventId = nextId();
        Instant firstArrival = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        List<Long> users = addWaitingCustomers(eventId, firstArrival, BATCH_SIZE + 1);
        Instant dueAt = firstArrival.plus(INTERVAL);
        int instances = 8;
        CountDownLatch ready = new CountDownLatch(instances);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(instances);

        try {
            List<Future<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < instances; i++) {
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("No se liberó la barrera de inicio");
                    }
                    return queueRepository.advanceDue(eventId, dueAt, BATCH_SIZE, INTERVAL, ENABLED_WINDOW);
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int enabledCount = 0;
            for (Future<Integer> attempt : attempts) {
                enabledCount += attempt.get(10, TimeUnit.SECONDS);
            }

            assertThat(enabledCount).isEqualTo(BATCH_SIZE);
            QueueEntrySnapshot next = queueRepository.findCurrent(eventId, users.get(BATCH_SIZE), dueAt).orElseThrow();
            assertThat(next.status()).isEqualTo("WAITING");
            assertThat(next.position()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Long> addWaitingCustomers(Long eventId, Instant at, int count) {
        List<Long> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Long userId = nextId();
            users.add(userId);
            queueRepository.join(eventId, userId, at);
        }
        return users;
    }

    private static Long nextId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
