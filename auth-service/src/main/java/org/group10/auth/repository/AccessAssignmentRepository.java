package org.group10.auth.repository;

import org.group10.auth.entity.AccessAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessAssignmentRepository extends JpaRepository<AccessAssignment, Long> {

    boolean existsByAccessStaffUserIdAndEventId(Long staffId, Long eventId);
}
