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
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
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
    void assign_alreadyAssigned_returnsTheExistingAssignmentWithoutSavingAnother() {
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff(STAFF_ID, null)));
        when(eventClient.findEvent(EVENT_A)).thenReturn(new EventOrganizationResponse(1L));
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_A)).thenReturn(true);

        AccessAssignmentOutcome outcome = accessAssignmentService.assign(organizerOfOrganization1, STAFF_ID, EVENT_A);

        verify(accessAssignmentRepository, never()).save(any());
        assertThat(outcome).isEqualTo(new AccessAssignmentOutcome(new AccessAssignmentResponse(STAFF_ID, EVENT_A), false));
    }

    @Test
    void assign_staffAlreadyAssignedToAnotherEvent_createsTheSecondAssignment() {
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff(STAFF_ID, null)));
        when(eventClient.findEvent(EVENT_B)).thenReturn(new EventOrganizationResponse(1L));
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_A)).thenReturn(true);
        when(accessAssignmentRepository.existsByAccessStaffUserIdAndEventId(STAFF_ID, EVENT_B)).thenReturn(false);

        AccessAssignmentOutcome outcome = accessAssignmentService.assign(organizerOfOrganization1, STAFF_ID, EVENT_B);

        ArgumentCaptor<AccessAssignment> saved = ArgumentCaptor.forClass(AccessAssignment.class);
        verify(accessAssignmentRepository).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo(EVENT_B);
        assertThat(outcome.created()).isTrue();
    }

    @Test
    void assign_staffOfAnotherOrganization_isForbiddenWithoutLookingUpTheEvent() {
        Organization otherOrganization = Organization.builder().id(2L).build();
        AccessStaff staffOfOrganization2 = AccessStaff.builder().userId(7L).organization(otherOrganization).build();
        when(accessStaffRepository.findById(7L)).thenReturn(Optional.of(staffOfOrganization2));

        assertError(() -> accessAssignmentService.assign(organizerOfOrganization1, 7L, EVENT_A),
                CommonErrorCode.FORBIDDEN);
        verifyNoInteractions(eventClient);
        verify(accessAssignmentRepository, never()).save(any());
    }

    @Test
    void assign_userWithoutAccessStaffRow_isAccessStaffNotFound() {
        when(accessStaffRepository.findById(1L)).thenReturn(Optional.empty());

        assertError(() -> accessAssignmentService.assign(organizerOfOrganization1, 1L, EVENT_A),
                AuthErrorCode.ACCESS_STAFF_NOT_FOUND);
        verifyNoInteractions(eventClient);
        verify(accessAssignmentRepository, never()).save(any());
    }

    @Test
    void assign_removedStaff_isAccessStaffNotFound() {
        when(accessStaffRepository.findById(STAFF_ID))
                .thenReturn(Optional.of(staff(STAFF_ID, LocalDateTime.of(2026, 1, 1, 0, 0))));

        assertError(() -> accessAssignmentService.assign(organizerOfOrganization1, STAFF_ID, EVENT_A),
                AuthErrorCode.ACCESS_STAFF_NOT_FOUND);
        verifyNoInteractions(eventClient);
        verify(accessAssignmentRepository, never()).save(any());
    }

    @Test
    void assign_eventOfAnotherOrganization_isForbiddenWithoutSaving() {
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff(STAFF_ID, null)));
        when(eventClient.findEvent(EVENT_B)).thenReturn(new EventOrganizationResponse(2L));

        assertError(() -> accessAssignmentService.assign(organizerOfOrganization1, STAFF_ID, EVENT_B),
                CommonErrorCode.FORBIDDEN);
        verify(accessAssignmentRepository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("eventServiceFailures")
    void assign_eventServiceFails_propagatesTheFailureWithoutSaving(RuntimeException failure) {
        when(accessStaffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff(STAFF_ID, null)));
        when(eventClient.findEvent(EVENT_A)).thenThrow(failure);

        assertThatThrownBy(() -> accessAssignmentService.assign(organizerOfOrganization1, STAFF_ID, EVENT_A))
                .isSameAs(failure);
        verify(accessAssignmentRepository, never()).save(any());
    }

    static Stream<RuntimeException> eventServiceFailures() {
        return Stream.of(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found", new HttpHeaders(), new byte[0],
                        StandardCharsets.UTF_8),
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "down", new HttpHeaders(),
                        new byte[0], StandardCharsets.UTF_8),
                new ResourceAccessException("event-service no respondió a tiempo"));
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
