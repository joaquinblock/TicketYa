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
class QueueTokenRedisIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Test
    void storeQueueTokenIfAbsent_repeatedWriteKeepsFirstToken() {
        Long eventId = nextId();
        Long userId = nextId();
        Instant firstArrival = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Duration interval = Duration.ofSeconds(30);
        Duration enabledWindow = Duration.ofMinutes(10);
        QueueEntrySnapshot entry = queueRepository.join(eventId, userId, firstArrival);
        Instant enabledAt = firstArrival.plus(interval);
        assertThat(queueRepository.advanceDue(eventId, enabledAt, 1, interval, enabledWindow)).isEqualTo(1);

        String firstToken = queueRepository.storeQueueTokenIfAbsent(eventId, entry.entryId(), "signed-token-1");
        String competingToken = queueRepository.storeQueueTokenIfAbsent(eventId, entry.entryId(), "signed-token-2");
        QueueEntrySnapshot current = queueRepository.findCurrent(eventId, userId, enabledAt).orElseThrow();

        assertThat(firstToken).isEqualTo("signed-token-1");
        assertThat(competingToken).isEqualTo(firstToken);
        assertThat(current.queueToken()).isEqualTo(firstToken);
    }

    private static Long nextId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}
