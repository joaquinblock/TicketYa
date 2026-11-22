package org.group10.queue.repository;

import org.group10.queue.entity.QueueEntrySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueRepositoryRedisIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Test
    void join_concurrentRequestsForSameCustomer_createOneArrival() throws Exception {
        int attempts = 12;
        Long eventId = nextId();
        Long userId = nextId();
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);

        try {
            List<Future<QueueEntrySnapshot>> results = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("No se liberó la barrera de inicio");
                        }
                        return queueRepository.join(eventId, userId, Instant.now());
                    }))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<QueueEntrySnapshot> entries = results.stream()
                    .map(future -> get(future))
                    .toList();

            assertThat(entries).extracting(QueueEntrySnapshot::entryId)
                    .containsOnly(entries.getFirst().entryId());
            assertThat(entries).extracting(QueueEntrySnapshot::position).containsOnly(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void join_distinctCustomers_areOrderedByArrival() {
        Long eventId = nextId();
        QueueEntrySnapshot first = queueRepository.join(eventId, nextId(), Instant.now());
        QueueEntrySnapshot second = queueRepository.join(eventId, nextId(), Instant.now());

        assertThat(first.position()).isEqualTo(1);
        assertThat(second.position()).isEqualTo(2);
    }

    private static Long nextId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private static QueueEntrySnapshot get(Future<QueueEntrySnapshot> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Se interrumpió la prueba de ingreso concurrente", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new AssertionError("El ingreso concurrente falló", e);
        }
    }
}
