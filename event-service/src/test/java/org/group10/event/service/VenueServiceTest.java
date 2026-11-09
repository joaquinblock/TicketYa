package org.group10.event.service;

import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.SectorResponse;
import org.group10.event.dto.VenueDetailResponse;
import org.group10.event.dto.VenueRequest;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    private static final Long VENUE_ID = 1L;

    @Mock
    private VenueRepository venueRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private SectorService sectorService;

    @InjectMocks
    private VenueService venueService;

    private final Venue venue = Venue.builder().id(VENUE_ID).name("Estadio").address("Av. 1").city("Tandil")
            .latitude(-37.0).longitude(-59.0).build();

    @Test
    void findById_capacityIsTheSumOfItsSectors() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(sectorService.findAll(VENUE_ID)).thenReturn(List.of(
                new SectorResponse(1L, "Campo", false, 5000), new SectorResponse(2L, "Platea", true, 80)));

        VenueDetailResponse detail = venueService.findById(VENUE_ID);

        assertThat(detail.capacity()).isEqualTo(5080);
        assertThat(detail.sectors()).hasSize(2);
    }

    @Test
    void findAll_withCity_filtersByIt() {
        when(venueRepository.findByCityIgnoreCaseOrderByNameAsc("Tandil")).thenReturn(List.of(venue));

        assertThat(venueService.findAll(" Tandil ")).hasSize(1);
        verify(venueRepository, never()).findAllByOrderByNameAsc();
    }

    @Test
    void update_replacesTheDataTrimmed() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));

        venueService.update(VENUE_ID, new VenueRequest(" Teatro ", "Calle 2", "Azul", -36.7, -59.8));

        assertThat(venue.getName()).isEqualTo("Teatro");
        assertThat(venue.getCity()).isEqualTo("Azul");
    }

    @Test
    void delete_venueWithEvents_isRejected() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(eventRepository.existsByVenueId(VENUE_ID)).thenReturn(true);

        assertThatThrownBy(() -> venueService.delete(VENUE_ID))
                .isInstanceOf(TicketYaException.class)
                .extracting(ex -> ((TicketYaException) ex).getErrorCode())
                .isEqualTo(EventErrorCode.VENUE_IN_USE);
        verify(venueRepository, never()).delete(any());
    }

    @Test
    void delete_venueWithoutEvents_removesSectorsAndSeatsFirst() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));

        venueService.delete(VENUE_ID);

        verify(sectorService).deleteAllOfVenue(VENUE_ID);
        verify(venueRepository).delete(venue);
    }

    @Test
    void findById_missingVenue_isNotFound() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.findById(VENUE_ID))
                .isInstanceOf(TicketYaException.class)
                .extracting(ex -> ((TicketYaException) ex).getErrorCode())
                .isEqualTo(EventErrorCode.VENUE_NOT_FOUND);
    }
}
