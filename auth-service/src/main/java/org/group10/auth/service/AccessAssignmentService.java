package org.group10.auth.service;

import lombok.RequiredArgsConstructor;
import org.group10.auth.repository.AccessAssignmentRepository;
import org.group10.auth.repository.AccessStaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessAssignmentService {

    private final AccessStaffRepository accessStaffRepository;
    private final AccessAssignmentRepository accessAssignmentRepository;

    public void ensureAssigned(Long staffId, Long eventId) {
    }
}
