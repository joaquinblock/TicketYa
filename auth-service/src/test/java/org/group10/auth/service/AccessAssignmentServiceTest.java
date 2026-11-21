package org.group10.auth.service;

import org.group10.auth.client.EventClient;
import org.group10.auth.client.dto.EventOrganizationResponse;
import org.group10.auth.dto.AccessAssignmentOutcome;
import org.group10.auth.dto.AccessAssignmentResponse;
import org.group10.auth.entity.AccessAssignment;
import org.group10.auth.entity.AccessStaff;
import org.group10.auth.entity.Organization;
import org.group10.auth.exception.AuthErrorCode;
import org.group10.auth.repository.AccessAssignmentRepository;
import org.group10.auth.repository.AccessStaffRepository;
import org.group10.common.exception.ErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
    @Mock
    private EventClient eventClient;

    @InjectMocks
    private AccessAssignmentService accessAssignmentService;

    private final Organization organization = Organization.builder().id(1L).build();
    private final Caller organizerOfOrganization1 = new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE"), 1L);

    @Test
    void assign_staffAndEventOfTheCallersOrganization_createsTheAssignment() {
        AccessStaff staff = staff(STAFF_ID, null);
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(eventClient.findEvent(EVENT_A)).thenReturn(new EventOrganizationResponse(1L));
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_A)).thenReturn(false);

        AccessAssignmentOutcome outcome = accessAssignmentService.assign(organizerOfOrganization1, STAFF_ID, EVENT_A);

        ArgumentCaptor<AccessAssignment> saved = ArgumentCaptor.forClass(AccessAssignment.class);
        verify(accessAssignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getAccessStaff()).isSameAs(staff);
        assertThat(saved.getValue().getEventId()).isEqualTo(EVENT_A);
        assertThat(outcome).isEqualTo(new AccessAssignmentOutcome(new AccessAssignmentResponse(STAFF_ID, EVENT_A), true));
    }

    @Test
    void ensureAssigned_staffAssignedToAnotherEvent_isStaffNotAssigned() {
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff(STAFF_ID, null)));
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_A)).thenReturn(true);
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_B)).thenReturn(false);

        assertError(() -> accessAssignmentService.ensureAssigned(STAFF_ID, EVENT_B), AuthErrorCode.STAFF_NOT_ASSIGNED);
    }

    @Test
    void ensureAssigned_userWithoutAccessStaffRow_isStaffNotAssigned() {
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.empty());

        assertError(() -> accessAssignmentService.ensureAssigned(STAFF_ID, EVENT_A), AuthErrorCode.STAFF_NOT_ASSIGNED);
    }

    @Test
    void ensureAssigned_removedStaffWithAnOldAssignment_isStaffNotAssigned() {
        when(accessStaffRepository.findById(STAFF_ID))
                .thenReturn(Optional.of(staff(STAFF_ID, LocalDateTime.of(2026, 1, 1, 0, 0))));
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_A)).thenReturn(true);

        assertError(() -> accessAssignmentService.ensureAssigned(STAFF_ID, EVENT_A), AuthErrorCode.STAFF_NOT_ASSIGNED);
    }

    @Test
    void ensureAssigned_activeStaffAssignedToTheEvent_passes() {
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff(STAFF_ID, null)));
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_A)).thenReturn(true);

        assertThatCode(() -> accessAssignmentService.ensureAssigned(STAFF_ID, EVENT_A)).doesNotThrowAnyException();
    }

    private AccessStaff staff(Long userId, LocalDateTime deletedAt) {
        return AccessStaff.builder().userId(userId).organization(organization).deletedAt(deletedAt).build();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(TicketYaException.class).extracting("errorCode").isEqualTo(code);
    }
}
