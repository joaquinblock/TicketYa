package org.group10.event.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.EventPageResponse;
import org.group10.event.dto.EventSearchRequest;
import org.group10.event.dto.EventSort;
import org.group10.event.dto.EventSummaryResponse;
import org.group10.event.entity.EventType;
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
 */
@SpringBootTest
@ActiveProfiles("test")
class EventSearchServiceTest {

    @Autowired
    private EventSearchService searchService;

    @Test
    void withoutFilters_showsOnlyPublishedAndSoldOutFromTheNearest() {
        assertThat(ids(search(null, null, null, null, null, null, null, null)))
                .containsExactly(6L, 5L, 11L, 2L, 1L, 4L);
    }

    @Test
    void byType() {
        assertThat(ids(search(null, EventType.CONCERT, null, null, null, null, null, null)))
                .containsExactly(6L, 11L, 1L);
    }

    @Test
    void byCity_ignoresCaseAndAccents() {
        assertThat(ids(search(null, null, "cordoba", null, null, null, null, null))).containsExactly(4L);
        assertThat(ids(search(null, null, "MAR DEL PLATA", null, null, null, null, null))).containsExactly(6L);
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
        assertThat(ids(search(null, EventType.CONCERT, "tandil", null, null, null, null, null))).containsExactly(11L);
    }

    @Test
    void sortByPrice_fromTheCheapestPriceFrom() {
        EventPageResponse page = search(null, null, null, null, null, null, null, EventSort.PRICE);

        assertThat(ids(page)).containsExactly(11L, 5L, 2L, 6L, 4L, 1L);
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
        EventSummaryResponse festival = search(null, EventType.FESTIVAL, null, null, null, null, null, null).content().getFirst();

        assertThat(festival.venue().city()).isEqualTo("Córdoba");
        assertThat(festival.participants()).containsExactly("DJ Marea", "Las Voces del Sur", "Los Serranos");
        assertThat(festival.priceFrom()).isEqualByComparingTo("38000");
    }

    @Test
    void paging_countsEveryResult() {
        EventPageResponse second = searchService.search(
                new EventSearchRequest(null, null, null, null, null, null, null, null, 1, 4));

        assertThat(ids(second)).containsExactly(1L, 4L);
        assertThat(second.totalElements()).isEqualTo(6);
        assertThat(second.totalPages()).isEqualTo(2);
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
        return searchService.search(new EventSearchRequest(q, type, city, participant, organizationId, from, to, sort,
                null, null));
    }

    private static List<Long> ids(EventPageResponse page) {
        return page.content().stream().map(EventSummaryResponse::id).toList();
    }
}
