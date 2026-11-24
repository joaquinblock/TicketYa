package org.group10.auth.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.auth.entity.AccessAssignment;
import org.group10.auth.entity.AccessStaff;
import org.group10.auth.repository.AccessAssignmentRepository;
import org.group10.auth.repository.AccessStaffRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Asigna el personal del {@link AuthSeeder} a eventos del seeder del event-service, para probar la puerta sin pasar
 * antes por la asignación. Corre después del {@code AuthSeeder}, que crea al personal.
 * <p>
 * El personal 4 (organización 1) valida en el 1 (Los Serranos en vivo), el 2, el 6 (agotado) y el 43; el 7
 * (organización 2) en el 11 (Peña folklórica serrana). Son ids del event-service, sin FK, como cualquier
 * {@code event_id}.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class AccessAssignmentSeeder implements ApplicationRunner {

    static final Map<Long, List<Long>> EVENTS_BY_STAFF = Map.of(
            4L, List.of(1L, 2L, 6L, 43L),
            7L, List.of(11L));

    private final AccessStaffRepository accessStaffRepository;
    private final AccessAssignmentRepository accessAssignmentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accessAssignmentRepository.count() > 0) {
            log.info("AccessAssignmentSeeder: ya hay asignaciones, se omite la carga");
            return;
        }
        List<AccessAssignment> assignments = EVENTS_BY_STAFF.entrySet().stream()
                .flatMap(entry -> staff(entry.getKey()).stream()
                        .flatMap(staff -> entry.getValue().stream()
                                .map(eventId -> AccessAssignment.builder().accessStaff(staff).eventId(eventId).build())))
                .toList();
        accessAssignmentRepository.saveAll(assignments);
        log.info("AccessAssignmentSeeder: {} asignaciones cargadas", assignments.size());
    }

    private Optional<AccessStaff> staff(Long userId) {
        return accessStaffRepository.findById(userId).filter(AccessStaff::isActive);
    }
}
