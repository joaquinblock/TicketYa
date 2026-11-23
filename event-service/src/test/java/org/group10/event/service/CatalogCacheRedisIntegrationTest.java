package org.group10.event.service;

import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.event.client.TicketClient;
import org.group10.event.dto.EventSearchRequest;
import org.group10.event.dto.EventSummaryResponse;
import org.group10.event.dto.ParticipantRequest;
import org.group10.event.dto.SectorUpdateRequest;
import org.group10.event.dto.VenueRequest;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Participant;
import org.group10.event.entity.ParticipantType;
import org.group10.event.entity.Sector;
import org.group10.event.entity.Venue;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.group10.event.repository.ParticipantRepository;
import org.group10.event.repository.SectorRepository;
import org.group10.event.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the MySQL and the Redis of {@code compose.yaml}. A change made with SQL, behind the services' back,
 * shows whether a read came from the cache; a change made through a service shows whether it evicted it.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogCacheRedisIntegrationTest {

    private static final Caller ADMIN = new Caller(3L, "ADMIN", Set.of("EVENT_ADMIN"), null);
    private static final Caller ORGANIZER = new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE"), 1L);
    private static final EventSearchRequest INITIAL_LIST =
            new EventSearchRequest(null, null, null, null, null, null, null, null, null, null, null);

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private TicketClient ticketClient;

    @Autowired
    private EventService eventService;
    @Autowired
    private EventSearchService eventSearchService;
    @Autowired
    private VenueService venueService;
    @Autowired
    private SectorService sectorService;
    @Autowired
    private ParticipantService participantService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventSectorRepository eventSectorRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private SectorRepository sectorRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;

    private final List<Long> eventIds = new ArrayList<>();
    private final List<Long> sectorIds = new ArrayList<>();
    private final List<Long> venueIds = new ArrayList<>();
    private final List<Long> participantIds = new ArrayList<>();

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @AfterEach
    void tearDown() {
        transactions.executeWithoutResult(tx -> {
            eventIds.forEach(eventSectorRepository::deleteAllByEventId);
            eventRepository.deleteAllById(eventIds);
            sectorRepository.deleteAllById(sectorIds);
            venueRepository.deleteAllById(venueIds);
            participantRepository.deleteAllById(participantIds);
        });
    }

    // ---- event detail ----

    @Test
    void findById_readAgain_comesFromTheCache() {
        long id = addEvent(EventStatus.PUBLISHED, seededVenue());
        String name = eventService.findById(id, Optional.empty()).name();

        renameEventBehindTheServicesBack(id);

        assertThat(eventService.findById(id, Optional.empty()).name()).isEqualTo(name);
    }

    @Test
    void findById_afterSuspend_readsTheNewStatus() {
        long id = addEvent(EventStatus.PUBLISHED, seededVenue());
        eventService.findById(id, Optional.empty());

        eventService.suspend(id, ADMIN);

        assertThat(eventService.findById(id, Optional.empty()).status()).isEqualTo(EventStatus.SUSPENDED);
    }

    @Test
    void findById_afterCancel_readsTheNewStatus() {
        long id = addEvent(EventStatus.PUBLISHED, seededVenue());
        eventService.findById(id, Optional.empty());

        eventService.cancel(id, ADMIN);

        assertThat(eventService.findById(id, Optional.empty()).status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void findById_afterSoldOut_readsTheNewStatus() {
        long id = addEvent(EventStatus.PUBLISHED, seededVenue());
        eventService.findById(id, Optional.empty());

        eventService.markSoldOut(id);

        assertThat(eventService.findById(id, Optional.empty()).status()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    void findById_afterTheJobFinishesIt_readsFinished() {
        long id = addEvent(EventStatus.PUBLISHED, seededVenue(), LocalDateTime.now().minusHours(3).withNano(0));
        eventService.findById(id, Optional.empty());

        eventService.finishEventsThatAlreadyHappened();

        assertThat(eventService.findById(id, Optional.empty()).status()).isEqualTo(EventStatus.FINISHED);
    }

    @Test
    void findById_draft_isNeverCached() {
        long id = addEvent(EventStatus.DRAFT, seededVenue());
        eventService.findById(id, Optional.of(ORGANIZER));

        String newName = renameEventBehindTheServicesBack(id);

        assertThat(eventService.findById(id, Optional.of(ORGANIZER)).name()).isEqualTo(newName);
        assertThatThrownBy(() -> eventService.findById(id, Optional.empty())).isInstanceOf(TicketYaException.class);
    }

    // ---- initial list ----

    @Test
    void search_initialListAgain_comesFromTheCache() {
        eventSearchService.search(INITIAL_LIST);

        long id = addEvent(EventStatus.PUBLISHED, seededVenue(), LocalDateTime.now().plusMinutes(5).withNano(0));

        assertThat(idsOf(eventSearchService.search(INITIAL_LIST).content())).doesNotContain(id);
    }

    @Test
    void search_withFilters_isNotCached() {
        EventSearchRequest byOrganization =
                new EventSearchRequest(null, null, null, null, null, 1L, null, null, null, null, null);
        eventSearchService.search(byOrganization);

        long id = addEvent(EventStatus.PUBLISHED, seededVenue(), LocalDateTime.now().plusMinutes(5).withNano(0));

        assertThat(idsOf(eventSearchService.search(byOrganization).content())).contains(id);
    }

    @Test
    void search_afterAnEventGoesBackOnSale_readsTheInitialListAgain() {
        long id = addEvent(EventStatus.SOLD_OUT, seededVenue(), LocalDateTime.now().plusMinutes(5).withNano(0));
        eventSearchService.search(INITIAL_LIST);
        jdbc.update("update event set name = 'Vuelve a la venta' where id = ?", id);

        eventService.markBackOnSale(id);

        assertThat(eventSearchService.search(INITIAL_LIST).content())
                .filteredOn(e -> e.id().equals(id))
                .singleElement()
                .satisfies(e -> assertThat(e.name() + ":" + e.status()).isEqualTo("Vuelve a la venta:PUBLISHED"));
    }

    // ---- venues, sectors and participants ----

    @Test
    void venueFindById_readAgain_comesFromTheCache() {
        Venue venue = addVenue();
        venueService.findById(venue.getId());

        jdbc.update("update venue set name = 'Cambiado por SQL' where id = ?", venue.getId());

        assertThat(venueService.findById(venue.getId()).name()).isEqualTo(venue.getName());
    }

    @Test
    void venueUpdate_evictsTheVenueAndTheEventsThatShowIt() {
        Venue venue = addVenue();
        long eventId = addEvent(EventStatus.PUBLISHED, venue);
        venueService.findById(venue.getId());
        eventService.findById(eventId, Optional.empty());

        venueService.update(venue.getId(), new VenueRequest("Nuevo nombre", venue.getAddress(), venue.getCity(),
                venue.getLatitude(), venue.getLongitude()));

        assertThat(venueService.findById(venue.getId()).name()).isEqualTo("Nuevo nombre");
        assertThat(eventService.findById(eventId, Optional.empty()).venue().name()).isEqualTo("Nuevo nombre");
    }

    @Test
    void sectorUpdate_evictsTheVenueAndTheEventsThatSellIt() {
        Venue venue = addVenue();
        Sector field = addSector(venue);
        long eventId = addEvent(EventStatus.PUBLISHED, venue);
        eventSectorRepository.saveAndFlush(EventSector.builder().event(eventRepository.getReferenceById(eventId))
                .sector(field).price(new BigDecimal("10000.00")).build());
        venueService.findById(venue.getId());
        eventService.findById(eventId, Optional.empty());

        sectorService.update(venue.getId(), field.getId(), new SectorUpdateRequest("Campo VIP", 300));

        assertThat(venueService.findById(venue.getId()).sectors()).extracting(s -> s.name()).containsExactly("Campo VIP");
        assertThat(eventService.findById(eventId, Optional.empty()).sectors()).extracting(s -> s.name())
                .containsExactly("Campo VIP");
    }

    @Test
    void participantFindById_readAgain_comesFromTheCache() {
        Participant participant = addParticipant();
        participantService.findById(participant.getId());

        jdbc.update("update participant set name = 'Cambiado por SQL' where id = ?", participant.getId());

        assertThat(participantService.findById(participant.getId()).name()).isEqualTo(participant.getName());
    }

    @Test
    void participantUpdate_evictsTheParticipantAndTheEventsThatShowIt() {
        Participant participant = addParticipant();
        long eventId = addEvent(EventStatus.PUBLISHED, seededVenue());
        Event event = eventRepository.findWithParticipantsByIdIn(List.of(eventId)).getFirst();
        event.setParticipants(new HashSet<>(Set.of(participant)));
        eventRepository.saveAndFlush(event);
        participantService.findById(participant.getId());
        eventService.findById(eventId, Optional.empty());

        String newName = "Renombrado " + System.nanoTime();
        participantService.update(participant.getId(), new ParticipantRequest(newName, ParticipantType.ARTIST));

        assertThat(participantService.findById(participant.getId()).name()).isEqualTo(newName);
        assertThat(eventService.findById(eventId, Optional.empty()).participants()).extracting(p -> p.name())
                .containsExactly(newName);
    }

    // ---- helpers ----

    private String renameEventBehindTheServicesBack(long id) {
        String newName = "Cambiado por SQL " + System.nanoTime();
        jdbc.update("update event set name = ? where id = ?", newName, id);
        return newName;
    }

    private static List<Long> idsOf(List<EventSummaryResponse> events) {
        return events.stream().map(EventSummaryResponse::id).toList();
    }

    private Venue seededVenue() {
        return venueRepository.findAll().getFirst();
    }

    private long addEvent(EventStatus status, Venue venue) {
        return addEvent(status, venue, LocalDateTime.now().plusDays(30).withNano(0));
    }

    private long addEvent(EventStatus status, Venue venue, LocalDateTime dateTime) {
        Event event = eventRepository.saveAndFlush(Event.builder()
                .name("Cache " + System.nanoTime())
                .type(EventType.CONCERT)
                .organizationId(1L)
                .createdBy(2L)
                .venue(venue)
                .dateTime(dateTime)
                .status(status)
                .highDemand(false)
                .build());
        eventIds.add(event.getId());
        return event.getId();
    }

    private Venue addVenue() {
        Venue venue = venueRepository.saveAndFlush(Venue.builder().name("Lugar " + System.nanoTime())
                .address("Calle 1").city("Tandil").latitude(-37.0).longitude(-59.0).build());
        venueIds.add(venue.getId());
        return venue;
    }

    private Sector addSector(Venue venue) {
        Sector sector = sectorRepository.saveAndFlush(Sector.builder().venue(venue).name("Campo").numbered(false)
                .capacity(300).build());
        sectorIds.add(sector.getId());
        return sector;
    }

    private Participant addParticipant() {
        Participant participant = participantRepository.saveAndFlush(Participant.builder()
                .name("Artista " + System.nanoTime()).type(ParticipantType.ARTIST).build());
        participantIds.add(participant.getId());
        return participant;
    }
}
