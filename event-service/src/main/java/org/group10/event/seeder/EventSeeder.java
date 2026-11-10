package org.group10.event.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Participant;
import org.group10.event.entity.ParticipantType;
import org.group10.event.entity.Seat;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.ParticipantRepository;
import org.group10.event.repository.SeatRepository;
import org.group10.event.repository.SectorRepository;
import org.group10.event.repository.VenueRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test data for the event-service. See {@code event-service.md}, section "Datos de prueba".
 * <p>
 * <b>The order of the inserts is a contract:</b> ticket-service's {@code TicketSeeder} uses events 1 and 2,
 * event sectors 1 to 4 and seats 1 to 154 by id. New data goes after the existing one, never in between.
 * <p>
 * Dates are relative to the day the service starts, so future events never end up in the past.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventSeeder implements ApplicationRunner {

    /** Del Sur Producciones and its organizer, from the auth-service's {@code AuthSeeder}. */
    static final Long ORGANIZATION_ID = 1L;
    static final Long ORGANIZER_USER_ID = 2L;

    /** Cultura Serrana and its organizer, from the auth-service's {@code AuthSeeder}. */
    static final Long OTHER_ORGANIZATION_ID = 2L;
    static final Long OTHER_ORGANIZER_USER_ID = 5L;

    private final VenueRepository venueRepository;
    private final SectorRepository sectorRepository;
    private final SeatRepository seatRepository;
    private final ParticipantRepository participantRepository;
    private final EventRepository eventRepository;
    private final EventSectorRepository eventSectorRepository;

    private LocalDate today;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (venueRepository.count() > 0) {
            log.info("EventSeeder: ya hay datos, se omite la carga");
            return;
        }
        today = LocalDate.now();

        // ---- Used by ticket-service by id: do not reorder ----

        Venue stadium = venueRepository.save(venue("Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires", -34.5453, -58.4498));
        Sector stadiumField = sectorRepository.save(generalAdmission(stadium, "Campo", 5000));
        Sector stadiumStand = sectorRepository.save(numbered(stadium, "Platea"));
        seedSeats(stadiumStand, List.of("A", "B", "C", "D"), 20);                        // seats 1-80

        Venue theater = venueRepository.save(venue("Teatro de la Sierra", "Calle Pinto 650", "Tandil", -37.3217, -59.1332));
        Sector theaterStalls = sectorRepository.save(numbered(theater, "Platea"));
        Sector theaterBalcony = sectorRepository.save(numbered(theater, "Pullman"));
        seedSeats(theaterStalls, List.of("A", "B", "C", "D", "E"), 10);                  // seats 81-130
        seedSeats(theaterBalcony, List.of("A", "B", "C"), 8);                            // seats 131-154

        Participant losSerranos = participantRepository.save(participant("Los Serranos", ParticipantType.ARTIST));
        Participant theaterCompany = participantRepository.save(participant("Compañía Teatro del Centro", ParticipantType.ARTIST));
        Participant anaTorres = participantRepository.save(participant("Ana Torres", ParticipantType.SPEAKER));

        // Event 1: high demand with the sale already open, so the ticket-service can sell it
        Event concert = eventRepository.save(base("Los Serranos en vivo", "Presentación del nuevo disco",
                EventType.CONCERT, stadium, at(33, 21, 0), EventStatus.PUBLISHED)
                .highDemand(true)
                .saleStart(at(-2, 10, 0))
                .maxTicketsPerCustomer(4)
                .participants(participants(losSerranos))
                .build());

        // Event 2: more than 72 hours ahead, so the pending return request of the ticket-service stays valid
        Event play = eventRepository.save(base("La casa de las sierras", "Comedia en dos actos",
                EventType.THEATER, theater, at(26, 20, 30), EventStatus.PUBLISHED)
                .maxTicketsPerCustomer(6)
                .participants(participants(theaterCompany))
                .build());

        Event talk = eventRepository.save(base("Arquitectura de microservicios", "Charla sobre diseño de sistemas distribuidos",
                EventType.CONFERENCE, theater, at(126, 18, 0), EventStatus.DRAFT)
                .participants(participants(anaTorres))
                .build());

        eventSectorRepository.saveAll(List.of(
                eventSector(concert, stadiumField, "45000"),                             // event_sector 1
                eventSector(concert, stadiumStand, "70000"),                             // event_sector 2
                eventSector(play, theaterStalls, "20000"),                               // event_sector 3
                eventSector(play, theaterBalcony, "15000"),                              // event_sector 4
                eventSector(talk, theaterStalls, "10000")                                // event_sector 5
        ));

        // ---- From here on, only the event-service depends on the ids ----

        Venue arena = venueRepository.save(venue("Arena Córdoba", "Av. Cárcano 1500", "Córdoba", -31.3686, -64.2456));
        Sector arenaField = sectorRepository.save(generalAdmission(arena, "Campo", 8000));
        Sector arenaLowerStand = sectorRepository.save(numbered(arena, "Platea Baja"));
        seedSeats(arenaLowerStand, List.of("A", "B", "C"), 15);

        Venue litoral = venueRepository.save(venue("Estadio del Litoral", "Av. Colón 2200", "Rosario", -32.9389, -60.6694));
        Sector litoralTerrace = sectorRepository.save(generalAdmission(litoral, "Popular", 12000));
        Sector litoralStand = sectorRepository.save(numbered(litoral, "Platea"));
        seedSeats(litoralStand, List.of("A", "B"), 20);

        Venue harborHall = venueRepository.save(venue("Sala del Puerto", "Av. Martínez de Hoz 4100", "Mar del Plata", -38.0333, -57.5389));
        Sector harborRoom = sectorRepository.save(generalAdmission(harborHall, "Sala", 300));

        Participant vocesDelSur = participantRepository.save(participant("Las Voces del Sur", ParticipantType.ARTIST));
        Participant djMarea = participantRepository.save(participant("DJ Marea", ParticipantType.ARTIST));
        Participant trioCostero = participantRepository.save(participant("Trío Costero", ParticipantType.ARTIST));
        Participant folkBallet = participantRepository.save(participant("Ballet Folklórico Serrano", ParticipantType.ARTIST));
        Participant central = participantRepository.save(participant("Club Atlético Central", ParticipantType.TEAM));
        Participant union = participantRepository.save(participant("Unión del Litoral", ParticipantType.TEAM));
        Participant martinIbarra = participantRepository.save(participant("Martín Ibarra", ParticipantType.SPEAKER));
        // In no event: it can be deleted (204), while any of the others gives 409 participant_in_use
        participantRepository.save(participant("Sofía Lanza", ParticipantType.SPEAKER));

        // The reference event with every field filled in: high demand whose sale opens in the future, several
        // participants, and both a general admission and a numbered sector. EventSeederTest keeps it that way.
        Event festival = eventRepository.save(base("Festival de Verano", "Tres escenarios y una noche entera de música",
                EventType.FESTIVAL, arena, at(60, 18, 0), EventStatus.PUBLISHED)
                .highDemand(true)
                .saleStart(at(20, 12, 0))
                .maxTicketsPerCustomer(6)
                .participants(participants(losSerranos, vocesDelSur, djMarea))
                .build());

        Event match = eventRepository.save(base("Central vs. Unión", "Clásico del Litoral, fecha 12 del torneo",
                EventType.SPORTS, litoral, at(15, 17, 0), EventStatus.PUBLISHED)
                .maxTicketsPerCustomer(4)
                .participants(participants(central, union))
                .build());

        Event soldOutJazz = eventRepository.save(base("Jazz en el puerto", "Noche de jazz frente al mar",
                EventType.CONCERT, harborHall, at(10, 22, 0), EventStatus.SOLD_OUT)
                .maxTicketsPerCustomer(2)
                .participants(participants(trioCostero))
                .build());

        Event suspendedTour = eventRepository.save(base("Los Serranos en Rosario", "Gira nacional, suspendida por mal tiempo",
                EventType.CONCERT, litoral, at(40, 21, 0), EventStatus.SUSPENDED)
                .maxTicketsPerCustomer(4)
                .participants(participants(losSerranos))
                .build());

        Event cancelledAcoustic = eventRepository.save(base("Recital acústico", "Formato íntimo, cancelado por el artista",
                EventType.CONCERT, arena, at(20, 21, 30), EventStatus.CANCELLED)
                .participants(participants(vocesDelSur))
                .build());

        Event finishedConference = eventRepository.save(base("Jornada de arquitectura de software", "Charlas y paneles sobre sistemas distribuidos",
                EventType.CONFERENCE, harborHall, at(-30, 9, 0), EventStatus.FINISHED)
                .participants(participants(anaTorres, martinIbarra))
                .build());

        // Draft with no sectors and no participants: it can't be published yet
        eventRepository.save(base("Muestra de fin de año", "Muestra de los talleres de teatro",
                EventType.THEATER, theater, at(50, 19, 0), EventStatus.DRAFT)
                .build());

        Event folkNight = eventRepository.save(base("Peña folklórica serrana", "Danzas y música del interior",
                EventType.CONCERT, theater, at(18, 21, 0), EventStatus.PUBLISHED)
                .organizationId(OTHER_ORGANIZATION_ID)
                .createdBy(OTHER_ORGANIZER_USER_ID)
                .maxTicketsPerCustomer(8)
                .participants(participants(folkBallet))
                .build());

        Event cultureTalks = eventRepository.save(base("Cultura y territorio", "Ciclo de charlas abiertas",
                EventType.CONFERENCE, theater, at(70, 18, 30), EventStatus.DRAFT)
                .organizationId(OTHER_ORGANIZATION_ID)
                .createdBy(OTHER_ORGANIZER_USER_ID)
                .participants(participants(martinIbarra))
                .build());

        eventSectorRepository.saveAll(List.of(
                eventSector(festival, arenaField, "38000"),
                eventSector(festival, arenaLowerStand, "55000"),
                eventSector(match, litoralTerrace, "12000"),
                eventSector(match, litoralStand, "25000"),
                eventSector(soldOutJazz, harborRoom, "18000"),
                eventSector(suspendedTour, litoralTerrace, "30000"),
                eventSector(suspendedTour, litoralStand, "48000"),
                eventSector(cancelledAcoustic, arenaLowerStand, "35000"),
                eventSector(finishedConference, harborRoom, "8000"),
                eventSector(folkNight, theaterStalls, "9000"),
                eventSector(folkNight, theaterBalcony, "7000"),
                eventSector(cultureTalks, theaterStalls, "3000")
        ));

        log.info("EventSeeder: {} lugares, {} sectores, {} asientos, {} participantes y {} eventos cargados",
                venueRepository.count(), sectorRepository.count(), seatRepository.count(),
                participantRepository.count(), eventRepository.count());
    }

    /**
     * An event of the first organization with the fields every event has; the caller adds the rest.
     */
    private static Event.EventBuilder base(String name, String description, EventType type, Venue venue,
                                           LocalDateTime dateTime, EventStatus status) {
        return Event.builder()
                .name(name)
                .description(description)
                .type(type)
                .organizationId(ORGANIZATION_ID)
                .createdBy(ORGANIZER_USER_ID)
                .venue(venue)
                .dateTime(dateTime)
                .status(status)
                .highDemand(false);
    }

    /**
     * @param daysFromToday negative for the past
     */
    private LocalDateTime at(int daysFromToday, int hour, int minute) {
        return today.plusDays(daysFromToday).atTime(hour, minute);
    }

    private void seedSeats(Sector sector, List<String> rows, int seatsPerRow) {
        List<Seat> seats = new ArrayList<>();
        for (String row : rows) {
            for (int number = 1; number <= seatsPerRow; number++) {
                seats.add(Seat.builder().sector(sector).row(row).number(number).build());
            }
        }
        seatRepository.saveAll(seats);
    }

    private static Set<Participant> participants(Participant... participants) {
        return new HashSet<>(Set.of(participants));
    }

    private static Venue venue(String name, String address, String city, double latitude, double longitude) {
        return Venue.builder().name(name).address(address).city(city).latitude(latitude).longitude(longitude).build();
    }

    private static Sector numbered(Venue venue, String name) {
        return Sector.builder().venue(venue).name(name).numbered(true).build();
    }

    private static Sector generalAdmission(Venue venue, String name, int capacity) {
        return Sector.builder().venue(venue).name(name).numbered(false).capacity(capacity).build();
    }

    private static Participant participant(String name, ParticipantType type) {
        return Participant.builder().name(name).type(type).build();
    }

    private static EventSector eventSector(Event event, Sector sector, String price) {
        return EventSector.builder()
                .event(event)
                .sector(sector)
                .price(new BigDecimal(price))
                .build();
    }
}
