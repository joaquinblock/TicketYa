package org.group10.auth.service;

import org.group10.auth.entity.AccessStaff;
import org.group10.auth.entity.Organization;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.AccessAssignmentRepository;
import org.group10.auth.repository.AccessStaffRepository;
import org.group10.common.exception.ErrorCode;
import org.group10.common.exception.TicketYaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessAssignmentServiceTest {

    private static final long STAFF_ID = 4L;
    private static final long EVENT_A = 10L;
    private static final long EVENT_B = 20L;

    @Mock
    private AccessStaffRepository accessStaffRepository;
    @Mock
    private AccessAssignmentRepository accessAssignmentRepository;

    @InjectMocks
    private AccessAssignmentService accessAssignmentService;

    private final Organization organization = Organization.builder().id(1L).build();

    @Test
    void ensureAssigned_staffAssignedToAnotherEvent_isStaffNotAssigned() {
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff(STAFF_ID, null)));
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_A)).thenReturn(true);
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_B)).thenReturn(false);

        assertError(() -> accessAssignmentService.ensureAssigned(STAFF_ID, EVENT_B), AuthErrorCode.STAFF_NOT_ASSIGNED);
    }

    private AccessStaff staff(Long userId, LocalDateTime deletedAt) {
        return AccessStaff.builder().userId(userId).organization(organization).deletedAt(deletedAt).build();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(TicketYaException.class).extracting("errorCode").isEqualTo(code);
    }
}
