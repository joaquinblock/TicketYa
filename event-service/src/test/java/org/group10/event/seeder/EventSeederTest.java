package org.group10.event.seeder;

import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Participant;
import org.group10.event.entity.Seat;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.ParticipantRepository;
import org.group10.event.repository.SeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the MySQL of {@code compose.yaml}: the seeder runs when the context starts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventSeederTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventSectorRepository eventSectorRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    // ---- Ids that ticket-service's TicketSeeder uses (ticket-service.md, "Datos de prueba") ----

    @Test
    void ticketServiceIds_eventsOneAndTwoAreTheConcertAndThePlay() {
        assertThat(event(1L).getName()).isEqualTo("Los Serranos en vivo");
        assertThat(event(2L).getName()).isEqualTo("La casa de las sierras");
    }

    @Test
    void ticketServiceIds_eventSectorsOneToFourBelongToTheConcertAndThePlay() {
        assertThat(eventSectorSummary(1L)).isEqualTo("1 Campo");
        assertThat(eventSectorSummary(2L)).isEqualTo("1 Platea");
        assertThat(eventSectorSummary(3L)).isEqualTo("2 Platea");
        assertThat(eventSectorSummary(4L)).isEqualTo("2 Pullman");
    }

    @Test
    void ticketServiceIds_seatsOneTo80AreFromTheStadiumAnd81To154FromTheTheater() {
        assertThat(venueOfSeat(1L)).isEqualTo(venueOfSeat(80L)).isEqualTo("Estadio del Sur");
        assertThat(venueOfSeat(81L)).isEqualTo(venueOfSeat(154L)).isEqualTo("Teatro de la Sierra");
        assertThat(venueOfSeat(155L)).isNotEqualTo("Teatro de la Sierra");
    }

    @Test
    void ticketServiceIds_thePlayIsMoreThan72HoursAhead_soItsReturnRequestIsValid() {
        assertThat(event(2L).getDateTime()).isAfter(LocalDateTime.now().plusHours(72));
    }

    // ---- Variety for search, publication and state transitions ----

    @Test
    void events_coverEveryStatusAndEveryType() {
        List<Event> events = eventRepository.findAll();

        assertThat(events).extracting(Event::getStatus).contains(EventStatus.values());
        assertThat(events).extracting(Event::getType).contains(EventType.values());
    }

    @Test
    void events_belongToBothOrganizationsWithTheirOwnOrganizer() {
        assertThat(eventRepository.findAll())
                .extracting(e -> e.getOrganizationId() + "/" + e.getCreatedBy())
                .containsOnly("1/2", "2/5")
                .contains("1/2", "2/5");
    }

    @Test
    void events_takePlaceInSeveralCities() {
        assertThat(eventRepository.findAll().stream().map(e -> e.getVenue().getCity()).collect(Collectors.toSet()))
                .contains("Buenos Aires", "Tandil", "Córdoba", "Rosario", "Mar del Plata");
    }

    @Test
    void events_onlyTheFinishedOneIsInThePast() {
        LocalDateTime now = LocalDateTime.now();

        assertThat(eventRepository.findAll()).allSatisfy(e -> {
            if (e.getStatus() == EventStatus.FINISHED) {
                assertThat(e.getDateTime()).isBefore(now);
            } else {
                assertThat(e.getDateTime()).isAfter(now);
            }
        });
    }

    @Test
    void events_highDemandOnesOpenTheSaleBeforeTheEvent() {
        assertThat(eventRepository.findAll()).filteredOn(Event::isHighDemand)
                .hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(e -> assertThat(e.getSaleStart()).isNotNull().isBefore(e.getDateTime()));
    }

    @Test
    void events_oneArtistPlaysInSeveralEvents_soTheParticipantFilterReturnsMoreThanOne() {
        assertThat(eventRepository.findAll())
                .filteredOn(e -> e.getParticipants().stream().anyMatch(p -> p.getName().equals("Los Serranos")))
                .hasSize(3);
    }

    @Test
    void events_theFestivalHasEveryFieldFilledIn() {
        Event festival = eventRepository.findAll().stream()
                .filter(e -> e.getName().equals("Festival de Verano")).findFirst().orElseThrow();

        // statusBeforeSuspension only has a value while the event is suspended
        assertThat(festival).hasNoNullFieldsOrPropertiesExcept("participants", "statusBeforeSuspension");
        assertThat(festival.isHighDemand()).isTrue();
        assertThat(festival.getParticipants()).hasSizeGreaterThan(1);
        assertThat(eventSectorRepository.findAll())
                .filteredOn(es -> es.getEvent().getId().equals(festival.getId()))
                .extracting(es -> es.getSector().isNumbered())
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    void participants_oneIsInNoEvent_soItCanBeDeleted() {
        Participant unassigned = participantRepository.findAll().stream()
                .filter(p -> p.getName().equals("Sofía Lanza")).findFirst().orElseThrow();

        assertThat(eventRepository.existsByParticipantsId(unassigned.getId())).isFalse();
    }

    @Test
    void eventSectors_numberedOnesHaveSeats_soNoSectorIsSoldWithCapacityZero() {
        assertThat(eventSectorRepository.findAll())
                .filteredOn(es -> es.getSector().isNumbered())
                .allSatisfy(es -> assertThat(seatRepository.countBySectorId(es.getSector().getId())).isPositive());
    }

    private Event event(Long id) {
        return eventRepository.findById(id).orElseThrow();
    }

    private String eventSectorSummary(Long id) {
        EventSector eventSector = eventSectorRepository.findById(id).orElseThrow();
        return eventSector.getEvent().getId() + " " + eventSector.getSector().getName();
    }

    private String venueOfSeat(Long id) {
        return seatRepository.findById(id).map(Seat::getSector).map(s -> s.getVenue().getName()).orElse("none");
    }
}
