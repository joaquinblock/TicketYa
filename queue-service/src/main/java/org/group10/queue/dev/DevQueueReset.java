package org.group10.queue.dev;

import lombok.extern.slf4j.Slf4j;
import org.group10.queue.repository.QueueRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/**
 * Filas de prueba para el perfil dev: vacía la fila de cada evento de alta demanda del seeder y le pone gente de
 * prueba adelante, para ver "N personas delante tuyo" antes del turno. Corre al arrancar y con
 * {@code POST /api/queue/{eventId}/dev-reset}. La gente de prueba pasa con la primera tanda, a los 30 s.
 */
@Component
@Profile("dev")
@Slf4j
public class DevQueueReset implements ApplicationRunner {

    /** Evento del seeder → personas de prueba delante. El front muestra la posición, que te cuenta a vos: 0 da "1" y 2 da "3". */
    static final Map<Long, Integer> PEOPLE_AHEAD_BY_EVENT = Map.of(1L, 0, 47L, 2);
    private static final long FIRST_TEST_USER_ID = 900_001L;

    private final QueueRepository queueRepository;
    private final Clock clock;

    public DevQueueReset(QueueRepository queueRepository, Clock clock) {
        this.queueRepository = queueRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        PEOPLE_AHEAD_BY_EVENT.keySet().forEach(this::reset);
    }

    public void reset(Long eventId) {
        queueRepository.clear(eventId);
        int peopleAhead = PEOPLE_AHEAD_BY_EVENT.getOrDefault(eventId, 0);
        for (int index = 0; index < peopleAhead; index++) {
            queueRepository.join(eventId, FIRST_TEST_USER_ID + index, clock.instant());
        }
        log.info("Fila del evento {} reiniciada con {} personas de prueba delante", eventId, peopleAhead);
    }
}
