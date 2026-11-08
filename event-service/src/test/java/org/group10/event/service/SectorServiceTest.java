package org.group10.event.service;

import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.SeatBatchRequest;
import org.group10.event.dto.SeatResponse;
import org.group10.event.dto.SeatRowRequest;
import org.group10.event.dto.SectorCreateRequest;
import org.group10.event.dto.SectorResponse;
import org.group10.event.dto.SectorUpdateRequest;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.Seat;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.SeatRepository;
import org.group10.event.repository.SectorRepository;
import org.group10.event.repository.SectorSeatCount;
import org.group10.event.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectorServiceTest {

    private static final Long VENUE_ID = 1L;
    private static final Long SECTOR_ID = 10L;

    @Mock
    private VenueRepository venueRepository;
    @Mock
    private SectorRepository sectorRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private EventSectorRepository eventSectorRepository;

    @InjectMocks
    private SectorService sectorService;

    private final Venue venue = Venue.builder().id(VENUE_ID).name("Teatro").address("Calle 1").city("Tandil")
            .latitude(-37.0).longitude(-59.0).build();

    @Test
    void create_numberedSectorWithRows_createsSeatsNumberedFromOneWithUppercaseRows() {
        givenVenueExists();
        when(sectorRepository.save(any(Sector.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(seatRepository.findExistingRows(SECTOR_ID, List.of("A", "B"))).thenReturn(List.of());
        when(seatRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SectorResponse response = sectorService.create(VENUE_ID, new SectorCreateRequest(" Platea ", true, null,
                List.of(new SeatRowRequest("a", 3), new SeatRowRequest("B", 2))));

        assertThat(response).isEqualTo(new SectorResponse(SECTOR_ID, "Platea", true, 5));
        ArgumentCaptor<List<Seat>> seats = seatsCaptor();
        verify(seatRepository).saveAll(seats.capture());
        assertThat(seats.getValue()).extracting(Seat::getRow, Seat::getNumber).containsExactly(
                tuple("A", 1), tuple("A", 2),
                tuple("A", 3), tuple("B", 1),
                tuple("B", 2));
    }

    @Test
    void create_generalAdmissionSector_usesItsCapacity() {
        givenVenueExists();
        when(sectorRepository.save(any(Sector.class))).thenAnswer(inv -> withId(inv.getArgument(0)));

        SectorResponse response = sectorService.create(VENUE_ID, new SectorCreateRequest("Campo", false, 5000, null));

        assertThat(response).isEqualTo(new SectorResponse(SECTOR_ID, "Campo", false, 5000));
        verify(seatRepository, never()).saveAll(anyList());
    }

    @Test
    void create_numberedSectorWithCapacity_isRejected() {
        givenVenueExists();

        assertErrorCode(() -> sectorService.create(VENUE_ID, new SectorCreateRequest("Platea", true, 100, null)),
                EventErrorCode.INVALID_SECTOR_CONFIGURATION);
    }

    @Test
    void create_generalAdmissionSectorWithoutCapacity_isRejected() {
        givenVenueExists();

        assertErrorCode(() -> sectorService.create(VENUE_ID, new SectorCreateRequest("Campo", false, null, null)),
                EventErrorCode.INVALID_SECTOR_CONFIGURATION);
    }

    @Test
    void create_generalAdmissionSectorWithRows_isRejected() {
        givenVenueExists();
        SectorCreateRequest request = new SectorCreateRequest("Campo", false, 100, List.of(new SeatRowRequest("A", 5)));

        assertErrorCode(() -> sectorService.create(VENUE_ID, request), EventErrorCode.INVALID_SECTOR_CONFIGURATION);
    }

    @Test
    void create_withNameAlreadyUsedInTheVenue_isRejected() {
        givenVenueExists();
        when(sectorRepository.existsByVenueIdAndNameIgnoreCase(VENUE_ID, "Campo")).thenReturn(true);

        assertErrorCode(() -> sectorService.create(VENUE_ID, new SectorCreateRequest("Campo", false, 100, null)),
                EventErrorCode.SECTOR_NAME_DUPLICATED);
        verify(sectorRepository, never()).save(any());
    }

    @Test
    void create_inMissingVenue_isRejected() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.empty());

        assertErrorCode(() -> sectorService.create(VENUE_ID, new SectorCreateRequest("Campo", false, 100, null)),
                EventErrorCode.VENUE_NOT_FOUND);
    }

    @Test
    void addSeatRows_withRowRepeatedInTheRequest_isRejected() {
        givenSector(numbered());
        SeatBatchRequest request = new SeatBatchRequest(List.of(new SeatRowRequest("C", 5), new SeatRowRequest("c", 5)));

        assertErrorCode(() -> sectorService.addSeatRows(VENUE_ID, SECTOR_ID, request), EventErrorCode.SEAT_ROW_DUPLICATED);
    }

    @Test
    void addSeatRows_withRowThatAlreadyExists_isRejected() {
        givenSector(numbered());
        when(seatRepository.findExistingRows(SECTOR_ID, List.of("A"))).thenReturn(List.of("A"));

        assertErrorCode(() -> sectorService.addSeatRows(VENUE_ID, SECTOR_ID,
                new SeatBatchRequest(List.of(new SeatRowRequest("A", 5)))), EventErrorCode.SEAT_ROW_DUPLICATED);
    }

    @Test
    void addSeatRows_toGeneralAdmissionSector_isRejected() {
        givenSector(generalAdmission(100));

        assertErrorCode(() -> sectorService.addSeatRows(VENUE_ID, SECTOR_ID,
                new SeatBatchRequest(List.of(new SeatRowRequest("A", 5)))), EventErrorCode.SECTOR_NOT_NUMBERED);
    }

    @Test
    void addSeatRows_whenAnEventAlreadyLeftDraft_isRejected() {
        givenSector(numbered());
        givenSectorUsedOutsideDraft();

        assertErrorCode(() -> sectorService.addSeatRows(VENUE_ID, SECTOR_ID,
                new SeatBatchRequest(List.of(new SeatRowRequest("Z", 5)))), EventErrorCode.SECTOR_IN_USE);
    }

    @Test
    void addSeatRows_returnsOnlyTheCreatedSeats() {
        givenSector(numbered());
        when(seatRepository.findExistingRows(SECTOR_ID, List.of("E"))).thenReturn(List.of());
        when(seatRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SeatResponse> seats = sectorService.addSeatRows(VENUE_ID, SECTOR_ID,
                new SeatBatchRequest(List.of(new SeatRowRequest("e", 2))));

        assertThat(seats).extracting(SeatResponse::row, SeatResponse::number).containsExactly(
                tuple("E", 1), tuple("E", 2));
    }

    @Test
    void update_capacityOfSectorUsedOutsideDraft_isRejected() {
        givenSector(generalAdmission(100));
        givenSectorUsedOutsideDraft();

        assertErrorCode(() -> sectorService.update(VENUE_ID, SECTOR_ID, new SectorUpdateRequest("Campo", 200)),
                EventErrorCode.SECTOR_IN_USE);
    }

    @Test
    void update_onlyTheName_doesNotCheckWhetherEventsUseTheSector() {
        Sector sector = generalAdmission(100);
        givenSector(sector);

        SectorResponse response = sectorService.update(VENUE_ID, SECTOR_ID, new SectorUpdateRequest("Campo VIP", 100));

        assertThat(response.name()).isEqualTo("Campo VIP");
        verify(eventSectorRepository, never()).existsBySectorIdAndEventStatusNot(any(), any());
    }

    @Test
    void update_numberedSectorWithCapacity_isRejected() {
        givenSector(numbered());

        assertErrorCode(() -> sectorService.update(VENUE_ID, SECTOR_ID, new SectorUpdateRequest("Platea", 10)),
                EventErrorCode.INVALID_SECTOR_CONFIGURATION);
    }

    @Test
    void delete_sectorSoldInAnyEvent_isRejected() {
        givenSector(numbered());
        when(eventSectorRepository.existsBySectorId(SECTOR_ID)).thenReturn(true);

        assertErrorCode(() -> sectorService.delete(VENUE_ID, SECTOR_ID), EventErrorCode.SECTOR_IN_USE);
        verify(sectorRepository, never()).delete(any());
    }

    @Test
    void delete_unusedSector_removesItsSeatsFirst() {
        Sector sector = numbered();
        givenSector(sector);

        sectorService.delete(VENUE_ID, SECTOR_ID);

        verify(seatRepository).deleteAllBySectorId(SECTOR_ID);
        verify(sectorRepository).delete(sector);
    }

    @Test
    void findById_ofSectorFromAnotherVenue_isNotFound() {
        when(venueRepository.existsById(VENUE_ID)).thenReturn(true);
        when(sectorRepository.findByIdAndVenueId(SECTOR_ID, VENUE_ID)).thenReturn(Optional.empty());

        assertErrorCode(() -> sectorService.findById(VENUE_ID, SECTOR_ID), EventErrorCode.SECTOR_NOT_FOUND);
    }

    @Test
    void findAll_numberedSectorsReportTheirSeatCountAsCapacity() {
        when(venueRepository.existsById(VENUE_ID)).thenReturn(true);
        when(seatRepository.countBySectorOfVenue(VENUE_ID)).thenReturn(List.of(new SectorSeatCount(SECTOR_ID, 80)));
        Sector field = Sector.builder().id(11L).venue(venue).name("Campo").numbered(false).capacity(5000).build();
        when(sectorRepository.findByVenueIdOrderByNameAsc(VENUE_ID)).thenReturn(List.of(field, numbered()));

        assertThat(sectorService.findAll(VENUE_ID)).extracting(SectorResponse::capacity).containsExactly(5000, 80);
    }

    private void givenVenueExists() {
        when(venueRepository.findById(VENUE_ID)).thenReturn(Optional.of(venue));
    }

    private void givenSector(Sector sector) {
        when(venueRepository.existsById(VENUE_ID)).thenReturn(true);
        when(sectorRepository.findByIdAndVenueId(SECTOR_ID, VENUE_ID)).thenReturn(Optional.of(sector));
    }

    private void givenSectorUsedOutsideDraft() {
        when(eventSectorRepository.existsBySectorIdAndEventStatusNot(SECTOR_ID, EventStatus.DRAFT)).thenReturn(true);
    }

    private Sector numbered() {
        return Sector.builder().id(SECTOR_ID).venue(venue).name("Platea").numbered(true).build();
    }

    private Sector generalAdmission(int capacity) {
        return Sector.builder().id(SECTOR_ID).venue(venue).name("Campo").numbered(false).capacity(capacity).build();
    }

    private static Sector withId(Sector sector) {
        sector.setId(SECTOR_ID);
        return sector;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Seat>> seatsCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private static void assertErrorCode(Runnable action, EventErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(TicketYaException.class)
                .extracting(ex -> ((TicketYaException) ex).getErrorCode())
                .isEqualTo(expected);
    }
}
