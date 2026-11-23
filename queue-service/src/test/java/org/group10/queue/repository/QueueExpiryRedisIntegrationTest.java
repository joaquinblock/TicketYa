package org.group10.queue.repository;

import org.group10.queue.entity.QueueEntrySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QueueExpiryRedisIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Test
    void findCurrent_enabledWindowExpired_marksEntryExpiredAndReentryGoesToTail() {
        Long eventId = nextId();
        Long userId = nextId();
        Long waitingUserId = nextId();
        Instant firstArrival = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Duration interval = Duration.ofSeconds(30);
        Duration enabledWindow = Duration.ofMinutes(10);
        QueueEntrySnapshot firstEntry = queueRepository.join(eventId, userId, firstArrival);
        queueRepository.join(eventId, waitingUserId, firstArrival);
        Instant enabledAt = firstArrival.plus(interval);
        Instant enabledUntil = enabledAt.plus(enabledWindow);

        assertThat(queueRepository.advanceDue(eventId, enabledAt, 1, interval, enabledWindow)).isEqualTo(1);

        QueueEntrySnapshot expired = queueRepository.findCurrent(eventId, userId, enabledUntil).orElseThrow();
        QueueEntrySnapshot reentered = queueRepository.join(eventId, userId, enabledUntil);
        QueueEntrySnapshot next = queueRepository.findCurrent(eventId, waitingUserId, enabledUntil).orElseThrow();

        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(expired.queueToken()).isNull();
        assertThat(reentered.entryId()).isNotEqualTo(firstEntry.entryId());
        assertThat(reentered.status()).isEqualTo("WAITING");
        assertThat(reentered.position()).isEqualTo(2);
        assertThat(next.position()).isEqualTo(1);
    }

    @Test
    void findCurrent_customerHasNotJoined_returnsEmpty() {
        assertThat(queueRepository.findCurrent(nextId(), nextId(), Instant.now())).isEmpty();
    }

    private static Long nextId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
