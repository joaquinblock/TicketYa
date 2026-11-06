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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventSeeder implements ApplicationRunner {

    private static final Long ORGANIZATION_ID = 1L;
    private static final Long ORGANIZER_USER_ID = 2L;

    private final VenueRepository venueRepository;
    private final SectorRepository sectorRepository;
    private final SeatRepository seatRepository;
    private final ParticipantRepository participantRepository;
    private final EventRepository eventRepository;
    private final EventSectorRepository eventSectorRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (venueRepository.count() > 0) {
            log.info("EventSeeder: ya hay datos, se omite la carga");
            return;
        }

        Venue stadium = venueRepository.save(venue("Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires", -34.5453, -58.4498));
        Sector field = sectorRepository.save(generalAdmission(stadium, "Campo", 5000));
        Sector stand = sectorRepository.save(numbered(stadium, "Platea"));
        seedSeats(stand, List.of("A", "B", "C", "D"), 20);

        Venue theater = venueRepository.save(venue("Teatro de la Sierra", "Calle Pinto 650", "Tandil", -37.3217, -59.1332));
        Sector stalls = sectorRepository.save(numbered(theater, "Platea"));
        Sector balcony = sectorRepository.save(numbered(theater, "Pullman"));
        seedSeats(stalls, List.of("A", "B", "C", "D", "E"), 10);
        seedSeats(balcony, List.of("A", "B", "C"), 8);

        Participant band = participantRepository.save(participant("Los Serranos", ParticipantType.ARTIST));
        Participant company = participantRepository.save(participant("Compañía Teatro del Centro", ParticipantType.ARTIST));
        Participant speaker = participantRepository.save(participant("Ana Torres", ParticipantType.SPEAKER));

        Event concert = eventRepository.save(Event.builder()
                .name("Los Serranos en vivo")
                .description("Presentación del nuevo disco")
                .type(EventType.CONCERT)
                .organizationId(ORGANIZATION_ID)
                .venue(stadium)
                .dateTime(LocalDateTime.of(2026, 12, 12, 21, 0))
                .status(EventStatus.PUBLISHED)
                .highDemand(true)
                .saleStart(LocalDateTime.of(2026, 11, 20, 10, 0))
                .maxTicketsPerCustomer(4)
                .createdBy(ORGANIZER_USER_ID)
                .participants(new HashSet<>(Set.of(band)))
                .build());

        Event play = eventRepository.save(Event.builder()
                .name("La casa de las sierras")
                .description("Comedia en dos actos")
                .type(EventType.THEATER)
                .organizationId(ORGANIZATION_ID)
                .venue(theater)
                .dateTime(LocalDateTime.of(2026, 12, 5, 20, 30))
                .status(EventStatus.PUBLISHED)
                .highDemand(false)
                .maxTicketsPerCustomer(6)
                .createdBy(ORGANIZER_USER_ID)
                .participants(new HashSet<>(Set.of(company)))
                .build());

        Event talk = eventRepository.save(Event.builder()
                .name("Arquitectura de microservicios")
                .description("Charla sobre diseño de sistemas distribuidos")
                .type(EventType.CONFERENCE)
                .organizationId(ORGANIZATION_ID)
                .venue(theater)
                .dateTime(LocalDateTime.of(2027, 3, 15, 18, 0))
                .status(EventStatus.DRAFT)
                .highDemand(false)
                .createdBy(ORGANIZER_USER_ID)
                .participants(new HashSet<>(Set.of(speaker)))
                .build());

        eventSectorRepository.saveAll(List.of(
                eventSector(concert, field, "45000"),
                eventSector(concert, stand, "70000"),
                eventSector(play, stalls, "20000"),
                eventSector(play, balcony, "15000"),
                eventSector(talk, stalls, "10000")
        ));

        log.info("EventSeeder: {} lugares, {} sectores, {} asientos, {} participantes y {} eventos cargados",
                venueRepository.count(), sectorRepository.count(), seatRepository.count(),
                participantRepository.count(), eventRepository.count());
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
                .occupied(sector.isNumbered() ? null : 0)
                .build();
    }
}
