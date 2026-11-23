package org.group10.event.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.EventPageResponse;
import org.group10.event.dto.EventSearchRequest;
import org.group10.event.dto.EventSort;
import org.group10.event.dto.EventSummaryResponse;
import org.group10.event.entity.EventType;
import org.group10.event.repository.ParticipantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the MySQL of {@code compose.yaml}, on the data of {@code EventSeeder} (dates relative to today).
 * <p>
 * Visible there: 1 Los Serranos en vivo (CONCERT, Buenos Aires, +33, from 45000), 2 La casa de las sierras (THEATER,
 * Tandil, +26, from 15000), 4 Festival de Verano (FESTIVAL, Córdoba, +60, from 38000, has Los Serranos), 5 Central vs.
 * Unión (SPORTS, Rosario, +15, from 12000), 6 Jazz en el puerto (CONCERT, SOLD_OUT, Mar del Plata, +10, from 18000) and
 * 11 Peña folklórica serrana (CONCERT, organization 2, Tandil, +18, from 7000). Left out: drafts 3, 10 and 12, the
 * suspended 7 (with Los Serranos), the cancelled 8 and the finished 9.
 * <p>
 * After them come the 30 billboard events (ids 13 to 42), from +75 on and from 50000 on, so the six above are
 * first by price and, after the sale showcase (43 to 46, +4 to +7), by date. That's why the lists check {@code startsWith} plus the total: the total is what
 * catches a draft, suspended or cancelled event that leaks in.
 */
@SpringBootTest
@ActiveProfiles("test")
class EventSearchServiceTest {

    private static final int VISIBLE_EVENTS = 6;
    private static final int BILLBOARD_EVENTS = 30;
    private static final int BILLBOARD_CONCERTS = 7;
    private static final int BILLBOARD_PLAYS = 6;
    private static final int BILLBOARD_CONCERTS_IN_TANDIL = 2;
    private static final int BILLBOARD_IN_CORDOBA = 7;
    private static final int BILLBOARD_IN_MAR_DEL_PLATA = 5;
    /** The sale showcase, events 43 to 46 (FESTIVAL and SPORTS, Buenos Aires, +4 to +7, from 52000): the nearest by date. */
    private static final int SHOWCASE_EVENTS = 4;

    @Autowired
    private EventSearchService searchService;

    @Autowired
    private ParticipantRepository participantRepository;

    @Test
    void withoutFilters_showsOnlyPublishedAndSoldOutFromTheNearest() {
        EventPageResponse page = search(null, null, null, null, null, null, null, null);

        assertThat(ids(page)).startsWith(43L, 44L, 45L, 46L, 6L, 5L, 11L, 2L, 1L, 4L);
        assertThat(page.totalElements()).isEqualTo(VISIBLE_EVENTS + BILLBOARD_EVENTS + SHOWCASE_EVENTS);
    }

    @Test
    void byType() {
        EventPageResponse page = search(null, EventType.CONCERT, null, null, null, null, null, null);

        assertThat(ids(page)).startsWith(6L, 11L, 1L);
        assertThat(page.totalElements()).isEqualTo(3 + BILLBOARD_CONCERTS);
    }

    @Test
    void byCity_ignoresCaseAndAccents() {
        EventPageResponse cordoba = search(null, null, "cordoba", null, null, null, null, null);
        EventPageResponse marDelPlata = search(null, null, "MAR DEL PLATA", null, null, null, null, null);

        assertThat(ids(cordoba)).startsWith(4L);
        assertThat(cordoba.totalElements()).isEqualTo(1 + BILLBOARD_IN_CORDOBA);
        assertThat(ids(marDelPlata)).startsWith(6L);
        assertThat(marDelPlata.totalElements()).isEqualTo(1 + BILLBOARD_IN_MAR_DEL_PLATA);
    }

    @Test
    void byParticipant_leavesTheSuspendedEventOut() {
        assertThat(ids(search(null, null, null, "SERRANOS", null, null, null, null))).containsExactly(1L, 4L);
    }

    @Test
    void byOrganization() {
        assertThat(ids(search(null, null, null, null, 2L, null, null, null))).containsExactly(11L);
    }

    @Test
    void byDateRange_includesBothDays() {
        LocalDate from = LocalDate.now().plusDays(15);
        LocalDate to = LocalDate.now().plusDays(26);

        assertThat(ids(search(null, null, null, null, null, from, to, null))).containsExactly(5L, 11L, 2L);
    }

    @Test
    void severalFilters_combine() {
        EventPageResponse page = search(null, EventType.CONCERT, "tandil", null, null, null, null, null);

        assertThat(ids(page)).startsWith(11L);
        assertThat(page.totalElements()).isEqualTo(1 + BILLBOARD_CONCERTS_IN_TANDIL);
    }

    @Test
    void sortByPrice_fromTheCheapestPriceFrom() {
        EventPageResponse page = search(null, null, null, null, null, null, null, EventSort.PRICE);

        assertThat(ids(page)).startsWith(11L, 5L, 2L, 6L, 4L, 1L);
        assertThat(page.content().getFirst().priceFrom()).isEqualByComparingTo("7000");
    }

    @Test
    void sortByRelevance_nameBeforeParticipantBeforeDescription() {
        // "serran": name of 1 (Los Serranos en vivo) and 11 (Peña folklórica serrana), participant of 4
        assertThat(ids(search("serran", null, null, null, null, null, null, EventSort.RELEVANCE)))
                .containsExactly(11L, 1L, 4L);
        // "disco": only in the description of 1
        assertThat(ids(search("disco", null, null, null, null, null, null, EventSort.RELEVANCE))).containsExactly(1L);
    }

    @Test
    void textWithWildcards_isTakenLiterally() {
        assertThat(ids(search("%", null, null, null, null, null, null, null))).isEmpty();
        assertThat(ids(search("_", null, null, null, null, null, null, null))).isEmpty();
    }

    @Test
    void summary_bringsVenueParticipantsAndPriceFrom() {
        EventSummaryResponse festival = search(null, EventType.FESTIVAL, "cordoba", null, null, null, null, null).content().getFirst();

        assertThat(festival.venue().city()).isEqualTo("Córdoba");
        assertThat(festival.participants()).containsExactly("DJ Marea", "Las Voces del Sur", "Los Serranos");
        assertThat(festival.priceFrom()).isEqualByComparingTo("38000");
    }

    @Test
    void summary_bringsTheSaleStart_orNullWhenTheSaleHasNoStart() {
        EventSummaryResponse festival = search(null, EventType.FESTIVAL, "cordoba", null, null, null, null, null).content().getFirst();
        EventSummaryResponse play = search(null, EventType.THEATER, null, null, null, null, null, null).content().getFirst();

        assertThat(festival.saleStart()).isEqualTo(LocalDate.now().plusDays(20).atTime(12, 0));
        assertThat(play.saleStart()).isNull();
    }

    @Test
    void paging_countsEveryResult() {
        EventPageResponse second = searchService.search(
                new EventSearchRequest(null, null, null, null, null, null, null, null, null, 1, 4));

        assertThat(ids(second)).containsExactly(6L, 5L, 11L, 2L);
        assertThat(second.totalElements()).isEqualTo(VISIBLE_EVENTS + BILLBOARD_EVENTS + SHOWCASE_EVENTS);
        assertThat(second.totalPages()).isEqualTo(10);
    }

    @Test
    void bySeveralTypes_matchesAnyOfThem_fromTheNearest() {
        EventPageResponse page = searchBy(List.of(EventType.CONCERT, EventType.THEATER), null);

        assertThat(ids(page)).startsWith(6L, 11L, 2L, 1L);
        assertThat(page.totalElements()).isEqualTo(4 + BILLBOARD_CONCERTS + BILLBOARD_PLAYS);
    }


    @Test
    void byParticipantId_leavesTheSuspendedEventOut() {
        assertThat(ids(searchBy(null, List.of(participantId("Los Serranos"))))).containsExactly(1L, 4L);
    }

    @Test
    void bySeveralParticipantIds_matchesAnyOfThem() {
        assertThat(ids(searchBy(null, List.of(participantId("Los Serranos"), participantId("Trío Costero")))))
                .containsExactly(6L, 1L, 4L);
    }

    @Test
    void byParticipantIdAndType_combine() {
        assertThat(ids(searchBy(List.of(EventType.FESTIVAL), List.of(participantId("Los Serranos")))))
                .containsExactly(4L);
    }

    @Test
    void byParticipantIdThatDoesNotExist_findsNothing() {
        assertThat(ids(searchBy(null, List.of(Long.MAX_VALUE)))).isEmpty();
    }

    @Test
    void fromAfterTo_isRejected() {
        assertThatThrownBy(() -> search(null, null, null, null, null, LocalDate.now().plusDays(5), LocalDate.now(), null))
                .isInstanceOf(TicketYaException.class)
                .extracting(e -> ((TicketYaException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    private EventPageResponse search(String q, EventType type, String city, String participant, Long organizationId,
                                     LocalDate from, LocalDate to, EventSort sort) {
        return searchService.search(new EventSearchRequest(q, type == null ? null : List.of(type), city, participant,
                null, organizationId, from, to, sort, null, null));
    }

    private EventPageResponse searchBy(List<EventType> types, List<Long> participantIds) {
        return searchService.search(new EventSearchRequest(null, types, null, null, participantIds, null, null, null,
                null, null, null));
    }

    private long participantId(String name) {
        return participantRepository.findAll().stream()
                .filter(participant -> participant.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    private static List<Long> ids(EventPageResponse page) {
        return page.content().stream().map(EventSummaryResponse::id).toList();
    }
}
