package org.group10.event.service;

import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.event.client.TicketClient;
import org.group10.event.dto.EventResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.EventType;
import org.group10.event.entity.Venue;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OwnEventsMySqlIntegrationTest {

    private final Long organizationId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
    private final List<Long> created = new ArrayList<>();

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private TicketClient ticketClient;

    @Autowired
    private EventService eventService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private VenueRepository venueRepository;

    @AfterEach
    void deleteCreatedEvents() {
        eventRepository.deleteAllById(created);
    }

    @Test
    void findOwn_organizer_returnsEveryStatusOfItsOrganizationNewestFirstWithTheVenue() {
        LocalDateTime base = LocalDateTime.now().plusMonths(3);
        Long older = save("Borrador viejo", base, EventStatus.DRAFT);
        Long newer = save("Cancelado nuevo", base.plusDays(10), EventStatus.CANCELLED);
        Long middle = save("Suspendido", base.plusDays(5), EventStatus.SUSPENDED);

        List<EventResponse> own = eventService.findOwn(organizer(organizationId));

        assertThat(own).extracting(EventResponse::id).containsExactly(newer, middle, older);
        assertThat(own).extracting(EventResponse::status)
                .containsExactly(EventStatus.CANCELLED, EventStatus.SUSPENDED, EventStatus.DRAFT);
        assertThat(own).allSatisfy(event -> {
            assertThat(event.organizationId()).isEqualTo(organizationId);
            assertThat(event.venue().name()).isNotBlank();
        });
    }

    @Test
    void findOwn_otherOrganization_doesNotSeeThem() {
        save("Ajeno", LocalDateTime.now().plusMonths(3), EventStatus.DRAFT);

        assertThat(eventService.findOwn(organizer(organizationId + 1))).isEmpty();
    }

    @Test
    void findOwn_tokenWithoutOrganization_returnsOrganizerNotInOrganization() {
        assertThatThrownBy(() -> eventService.findOwn(organizer(null)))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(EventErrorCode.ORGANIZER_NOT_IN_ORGANIZATION);
    }

    private Long save(String name, LocalDateTime dateTime, EventStatus status) {
        Venue venue = venueRepository.findAll().getFirst();
        Event event = Event.builder().name(name).type(EventType.CONCERT).organizationId(organizationId)
                .venue(venue).dateTime(dateTime).status(status).highDemand(false).createdBy(2L).build();
        Long id = eventRepository.saveAndFlush(event).getId();
        created.add(id);
        return id;
    }

    private static Caller organizer(Long organizationId) {
        return new Caller(2L, "ORGANIZER", Set.of("EVENT_MANAGE"), organizationId);
    }
}
