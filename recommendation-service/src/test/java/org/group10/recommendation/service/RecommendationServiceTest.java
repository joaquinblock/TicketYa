package org.group10.recommendation.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.recommendation.client.EventClient;
import org.group10.recommendation.dto.EventDetail;
import org.group10.recommendation.dto.EventPage;
import org.group10.recommendation.dto.EventSummary;
import org.group10.recommendation.entity.CustomerPreferences;
import org.group10.recommendation.entity.EventType;
import org.group10.recommendation.entity.PurchaseHistory;
import org.group10.recommendation.repository.PreferencesRepository;
import org.group10.recommendation.repository.PurchaseHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final Caller CLIENT = new Caller(7L, "CLIENT", Set.of(), null);
    private static final Caller ORGANIZER = new Caller(8L, "ORGANIZER", Set.of(), 1L);
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T15:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private PreferencesRepository preferencesRepository;

    @Mock
    private PurchaseHistoryRepository purchaseHistoryRepository;

    @Mock
    private PurchaseHistoryService purchaseHistoryService;

    @Mock
    private EventClient eventClient;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(preferencesRepository, purchaseHistoryRepository,
                purchaseHistoryService, eventClient, CLOCK);
    }

    @Test
    void nonClientIsForbiddenWithoutAskingAnything() {
        assertThatThrownBy(() -> recommendationService.suggest(ORGANIZER))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.FORBIDDEN);
        verifyNoInteractions(preferencesRepository, purchaseHistoryRepository, purchaseHistoryService, eventClient);
    }

    @Test
    void favoriteParticipantRanksAboveTypeAndCityAndTiesGoByDate() {
        preferences(List.of(EventType.CONCERT), List.of(10L), "Tandil");
        EventSummary withFavorite = event(1L, "THEATER", "Rosario", 30);
        EventSummary concertLater = event(2L, "CONCERT", "Córdoba", 20);
        EventSummary inTandilSooner = event(3L, "SPORTS", "Tandil", 10);
        byParticipants(List.of(10L), withFavorite);
        byTypes(List.of(EventType.CONCERT), concertLater);
        byCity("Tandil", inTandilSooner);

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(1L, 3L, 2L);
    }

    @Test
    void pointsAddUpWhenAnEventMatchesSeveralPreferences() {
        preferences(List.of(EventType.CONCERT), List.of(10L), "Tandil");
        EventSummary favoriteOnly = event(1L, "THEATER", "Rosario", 5);
        EventSummary concertInTandil = event(2L, "CONCERT", "Tandil", 40);
        byParticipants(List.of(10L), favoriteOnly);
        byTypes(List.of(EventType.CONCERT), concertInTandil);
        byCity("Tandil", concertInTandil);

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(2L, 1L);
    }

    @Test
    void cityMatchesWithoutCaseOrAccents() {
        preferences(List.of(EventType.THEATER), List.of(), "cordoba");
        EventSummary theaterInCordoba = event(1L, "THEATER", "Córdoba", 30);
        EventSummary theaterElsewhere = event(2L, "THEATER", "Rosario", 10);
        byTypes(List.of(EventType.THEATER), theaterElsewhere, theaterInCordoba);
        byCity("cordoba");

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(1L, 2L);
    }

    @Test
    void onlyAsksForThePreferencesTheClientHas() {
        preferences(List.of(EventType.CONCERT), List.of(), null);
        byTypes(List.of(EventType.CONCERT), event(1L, "CONCERT", "Tandil", 10));

        recommendationService.suggest(CLIENT);

        verify(eventClient, never()).search(anyList(), isNull(), isNull(), anyInt());
        verify(eventClient, never()).search(isNull(), isNull(), any(String.class), anyInt());
    }

    @Test
    void eventsAlreadyBoughtAreNotSuggested() {
        preferences(List.of(EventType.CONCERT), List.of(), null);
        history(1L);
        when(purchaseHistoryService.coPurchased(CLIENT.userId(), List.of(1L), RecommendationService.CO_PURCHASE_LIMIT))
                .thenReturn(Map.of());
        byTypes(List.of(EventType.CONCERT), event(1L, "CONCERT", "Tandil", 10), event(2L, "CONCERT", "Tandil", 20));

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(2L);
    }

    @Test
    void coPurchasedEventsAddOnePointPerBuyerUpToThree() {
        history(1L);
        coPurchased(Map.of(20L, 2, 21L, 5));
        when(eventClient.findEvent(20L)).thenReturn(detail(20L, "PUBLISHED", 10, List.of()));
        when(eventClient.findEvent(21L)).thenReturn(detail(21L, "SOLD_OUT", 20, List.of()));
        preferences(List.of(EventType.THEATER), List.of(), null);
        byTypes(List.of(EventType.THEATER), event(30L, "THEATER", "Tandil", 5));

        // 21: tope de 3 por 5 clientes. 30: obra, +2. 20: dos clientes, +2 pero más tarde que 30.
        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(21L, 30L, 20L);
    }

    @Test
    void coPurchasedEventWithAFavoriteParticipantGetsThosePointsToo() {
        history(1L);
        coPurchased(Map.of(20L, 1));
        when(eventClient.findEvent(20L)).thenReturn(detail(20L, "PUBLISHED", 30, List.of(10L)));
        preferences(List.of(), List.of(10L), null);
        byParticipants(List.of(10L), event(31L, "THEATER", "Rosario", 10));

        // 20: favorito (+3) y un cliente (+1) le gana a 31, que solo tiene el favorito
        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(20L, 31L);
    }

    @Test
    void coPurchasedEventsThatCannotBeBoughtAreDropped() {
        history(1L);
        coPurchased(Map.of(20L, 1, 21L, 1, 22L, 1, 23L, 1));
        when(eventClient.findEvent(20L)).thenReturn(detail(20L, "SUSPENDED", 10, List.of()));
        when(eventClient.findEvent(21L)).thenReturn(detail(21L, "PUBLISHED", -1, List.of()));
        when(eventClient.findEvent(22L)).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                null, null, null));
        when(eventClient.findEvent(23L)).thenReturn(detail(23L, "PUBLISHED", 10, List.of()));

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(23L);
    }

    @Test
    void coPurchasedDetailBecomesTheSameSummaryAsTheSearch() {
        history(1L);
        coPurchased(Map.of(20L, 1));
        when(eventClient.findEvent(20L)).thenReturn(detail(20L, "PUBLISHED", 10, List.of(10L)));

        EventSummary summary = recommendationService.suggest(CLIENT).getFirst();

        assertThat(summary.id()).isEqualTo(20L);
        assertThat(summary.priceFrom()).isEqualByComparingTo("20000");
        assertThat(summary.participants()).containsExactly("Participante 10");
        assertThat(summary.venue().city()).isEqualTo("Tandil");
    }

    @Test
    void withoutPreferencesOrPurchasesSuggestsTheUpcomingEvents() {
        upcoming(event(1L, "CONCERT", "Tandil", 10), event(2L, "THEATER", "Rosario", 20));

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(1L, 2L);
    }

    @Test
    void whenNothingMatchesSuggestsTheUpcomingEventsNotBoughtYet() {
        preferences(List.of(EventType.CONFERENCE), List.of(), null);
        history(1L);
        coPurchased(Map.of());
        byTypes(List.of(EventType.CONFERENCE));
        upcoming(event(1L, "CONCERT", "Tandil", 10), event(2L, "THEATER", "Rosario", 20));

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(2L);
    }

    @Test
    void returnsAtMostTen() {
        preferences(List.of(EventType.CONCERT), List.of(), null);
        byTypes(List.of(EventType.CONCERT), IntStream.rangeClosed(1, 15)
                .mapToObj(i -> event(i, "CONCERT", "Tandil", i))
                .toArray(EventSummary[]::new));

        assertThat(ids(recommendationService.suggest(CLIENT))).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    private void preferences(List<EventType> types, List<Long> participantIds, String city) {
        when(preferencesRepository.findById(CLIENT.userId())).thenReturn(Optional.of(CustomerPreferences.builder()
                .userId(CLIENT.userId())
                .eventTypes(types)
                .participantIds(participantIds)
                .city(city)
                .build()));
    }

    private void history(Long... eventIds) {
        when(purchaseHistoryRepository.findById(CLIENT.userId())).thenReturn(Optional.of(PurchaseHistory.builder()
                .userId(CLIENT.userId())
                .eventIds(List.of(eventIds))
                .build()));
    }

    private void coPurchased(Map<Long, Integer> buyersByEvent) {
        when(purchaseHistoryService.coPurchased(eq(CLIENT.userId()), anyList(),
                eq(RecommendationService.CO_PURCHASE_LIMIT))).thenReturn(new LinkedHashMap<>(buyersByEvent));
    }

    private void byParticipants(List<Long> ids, EventSummary... events) {
        when(eventClient.search(eq(ids), isNull(), isNull(), anyInt())).thenReturn(page(events));
    }

    private void byTypes(List<EventType> types, EventSummary... events) {
        when(eventClient.search(isNull(), eq(types), isNull(), anyInt())).thenReturn(page(events));
    }

    private void byCity(String city, EventSummary... events) {
        when(eventClient.search(isNull(), isNull(), eq(city), anyInt())).thenReturn(page(events));
    }

    private void upcoming(EventSummary... events) {
        when(eventClient.search(isNull(), isNull(), isNull(), anyInt())).thenReturn(page(events));
    }

    private static EventPage page(EventSummary... events) {
        return new EventPage(List.of(events));
    }

    private static EventSummary event(long id, String type, String city, int daysFromNow) {
        return new EventSummary(id, "Evento " + id, type, "PUBLISHED", NOW.plusDays(daysFromNow), false, 1L,
                new EventSummary.Venue(1L, "Lugar", "Calle 1", city, null, null), new BigDecimal("10000"),
                List.of());
    }

    private static EventDetail detail(long id, String status, int daysFromNow, List<Long> participantIds) {
        return new EventDetail(id, "Evento " + id, "CONCERT", 1L,
                new EventSummary.Venue(1L, "Lugar", "Calle 1", "Tandil", null, null), NOW.plusDays(daysFromNow),
                status, false,
                List.of(new EventDetail.Sector(new BigDecimal("35000")), new EventDetail.Sector(new BigDecimal("20000"))),
                participantIds.stream().map(p -> new EventDetail.Participant(p, "Participante " + p)).toList());
    }

    private static List<Long> ids(List<EventSummary> events) {
        return events.stream().map(EventSummary::id).toList();
    }
}
