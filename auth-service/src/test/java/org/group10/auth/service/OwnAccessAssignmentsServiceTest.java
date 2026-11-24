package org.group10.auth.service;

import org.group10.auth.client.EventClient;
import org.group10.auth.dto.AccessAssignmentResponse;
import org.group10.auth.entity.AccessAssignment;
import org.group10.auth.entity.AccessStaff;
import org.group10.auth.repository.AccessAssignmentRepository;
import org.group10.auth.repository.AccessStaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnAccessAssignmentsServiceTest {

    @Mock
    private AccessStaffRepository accessStaffRepository;
    @Mock
    private AccessAssignmentRepository accessAssignmentRepository;
    @Mock
    private EventClient eventClient;

    @InjectMocks
    private AccessAssignmentService accessAssignmentService;

    @Test
    void findOwn_activeStaff_returnsItsEventsInOrder() {
        AccessStaff staff = AccessStaff.builder().userId(4L).build();
        when(accessStaffRepository.findById(4L)).thenReturn(Optional.of(staff));
        when(accessAssignmentRepository.findByAccessStaffUserIdOrderByEventId(4L)).thenReturn(List.of(
                AccessAssignment.builder().accessStaff(staff).eventId(1L).build(),
                AccessAssignment.builder().accessStaff(staff).eventId(6L).build()));

        assertThat(accessAssignmentService.findOwn(4L)).containsExactly(
                new AccessAssignmentResponse(4L, 1L), new AccessAssignmentResponse(4L, 6L));
        verifyNoInteractions(eventClient);
    }

    @Test
    void findOwn_staffDeactivated_returnsNothing() {
        AccessStaff staff = AccessStaff.builder().userId(4L).deletedAt(LocalDateTime.now()).build();
        when(accessStaffRepository.findById(4L)).thenReturn(Optional.of(staff));

        assertThat(accessAssignmentService.findOwn(4L)).isEmpty();
        verifyNoInteractions(accessAssignmentRepository);
    }

    @Test
    void findOwn_notAccessStaff_returnsNothing() {
        when(accessStaffRepository.findById(2L)).thenReturn(Optional.empty());

        assertThat(accessAssignmentService.findOwn(2L)).isEmpty();
    }
}
