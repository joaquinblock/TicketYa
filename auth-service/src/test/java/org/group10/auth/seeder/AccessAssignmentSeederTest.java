package org.group10.auth.seeder;

import org.group10.auth.entity.AccessAssignment;
import org.group10.auth.entity.AccessStaff;
import org.group10.auth.repository.AccessAssignmentRepository;
import org.group10.auth.repository.AccessStaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessAssignmentSeederTest {

    @Mock
    private AccessStaffRepository accessStaffRepository;
    @Mock
    private AccessAssignmentRepository accessAssignmentRepository;

    @InjectMocks
    private AccessAssignmentSeeder seeder;

    @Test
    void run_emptyDatabase_assignsEachStaffToItsEvents() {
        when(accessAssignmentRepository.count()).thenReturn(0L);
        when(accessStaffRepository.findById(4L)).thenReturn(Optional.of(AccessStaff.builder().userId(4L).build()));
        when(accessStaffRepository.findById(7L)).thenReturn(Optional.of(AccessStaff.builder().userId(7L).build()));

        seeder.run(new DefaultApplicationArguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AccessAssignment>> saved = ArgumentCaptor.forClass(List.class);
        verify(accessAssignmentRepository).saveAll(saved.capture());
        assertThat(saved.getValue())
                .extracting(assignment -> assignment.getAccessStaff().getUserId() + "->" + assignment.getEventId())
                .containsExactlyInAnyOrder("4->1", "4->2", "4->6", "4->43", "7->11");
    }

    @Test
    void run_alreadySeeded_doesNotAssignAgain() {
        when(accessAssignmentRepository.count()).thenReturn(5L);

        seeder.run(new DefaultApplicationArguments());

        verify(accessAssignmentRepository, never()).saveAll(anyList());
    }
}
