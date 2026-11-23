package org.group10.queue.service;

import org.group10.queue.repository.QueueRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class QueueBatchScheduler {

    private final QueueRepository queueRepository;
    private final Clock clock;

    public QueueBatchScheduler(QueueRepository queueRepository, Clock clock) {
        this.queueRepository = queueRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000)
    public void advanceDueBatches() {
        queueRepository.advanceDueQueues(clock.instant());
    }
}
