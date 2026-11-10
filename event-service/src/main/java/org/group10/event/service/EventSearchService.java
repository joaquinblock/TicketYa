package org.group10.event.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.EventPageResponse;
import org.group10.event.dto.EventSearchRequest;
import org.group10.event.dto.EventSort;
import org.group10.event.dto.EventSummaryResponse;
import org.group10.event.entity.Event;
import org.group10.event.entity.EventSector;
import org.group10.event.entity.EventStatus;
import org.group10.event.entity.Participant;
import org.group10.event.mapper.VenueMapper;
import org.group10.event.repository.EventPriceFrom;
import org.group10.event.repository.EventRepository;
import org.group10.event.repository.EventSectorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Search of the catalog (consigna 9): what a client can buy, or could buy soon. See {@code event-search.md}.
 * <p>
 * Filtering, ordering and paging run in the database with the Criteria API, so a filter that isn't sent simply
 * isn't added. A page costs four queries: count, page, participants of the page and their cheapest prices.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventSearchService {

    /** Statuses the search shows. SUSPENDED, CANCELLED and FINISHED are left out on purpose (see event-search.md). */
    static final Set<EventStatus> VISIBLE = Set.of(EventStatus.PUBLISHED, EventStatus.SOLD_OUT);

    private static final char LIKE_ESCAPE = '\\';
    /** Events with no price go last when ordering by price. */
    private static final BigDecimal NO_PRICE = new BigDecimal("9999999999.99");

    private final EntityManager entityManager;
    private final EventRepository eventRepository;
    private final EventSectorRepository eventSectorRepository;
    private final Clock clock;

    /**
     * @throws TicketYaException {@code INVALID_PARAMETER} if {@code from} is after {@code to}
     */
    public EventPageResponse search(EventSearchRequest request) {
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new TicketYaException(CommonErrorCode.INVALID_PARAMETER,
                    "from (%s) no puede ser posterior a to (%s)".formatted(request.from(), request.to()));
        }
        Filters filters = Filters.of(request, LocalDateTime.now(clock));
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<Event> counted = count.from(Event.class);
        count.select(cb.count(counted)).where(filters.toPredicates(cb, count, counted));
        long total = entityManager.createQuery(count).getSingleResult();

        int page = request.pageOrDefault();
        int size = request.sizeOrDefault();
        CriteriaQuery<Event> select = cb.createQuery(Event.class);
        Root<Event> event = select.from(Event.class);
        event.fetch("venue");
        select.select(event)
                .where(filters.toPredicates(cb, select, event))
                .orderBy(order(cb, select, event, request.sortOrDefault(), filters));
        List<Event> events = entityManager.createQuery(select)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        int totalPages = (int) Math.ceil((double) total / size);
        return new EventPageResponse(summaries(events), page, size, total, totalPages);
    }

    private static List<Order> order(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Event> event, EventSort sort,
                                     Filters filters) {
        List<Order> order = new ArrayList<>();
        if (sort == EventSort.PRICE) {
            Subquery<BigDecimal> priceFrom = query.subquery(BigDecimal.class);
            Root<EventSector> sector = priceFrom.from(EventSector.class);
            priceFrom.select(cb.min(sector.get("price"))).where(cb.equal(sector.get("event"), event));
            order.add(cb.asc(cb.coalesce(priceFrom, NO_PRICE)));
        } else if (sort == EventSort.RELEVANCE && filters.text() != null) {
            Expression<Integer> score = cb.<Integer>selectCase()
                    .when(like(cb, event.get("name"), filters.text()), 3)
                    .when(cb.exists(participantNamed(cb, query, event, filters.text())), 2)
                    .when(like(cb, event.get("description"), filters.text()), 1)
                    .otherwise(0);
            order.add(cb.desc(score));
        }
        order.add(cb.asc(event.get("dateTime")));
        order.add(cb.asc(event.get("id")));
        return order;
    }

    /**
     * Participants and prices of the page, in one query each: loading them per event would make one query per result.
     */
    private List<EventSummaryResponse> summaries(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> ids = events.stream().map(Event::getId).toList();
        Map<Long, List<String>> participants = eventRepository.findWithParticipantsByIdIn(ids).stream()
                .collect(Collectors.toMap(Event::getId, e -> e.getParticipants().stream()
                        .map(Participant::getName).sorted().toList()));
        Map<Long, BigDecimal> prices = eventSectorRepository.findPriceFromByEventIds(ids).stream()
                .collect(Collectors.toMap(EventPriceFrom::eventId, EventPriceFrom::price));
        return events.stream()
                .map(e -> new EventSummaryResponse(e.getId(), e.getName(), e.getType(), e.getStatus(), e.getDateTime(),
                        e.isHighDemand(), e.getOrganizationId(), VenueMapper.toSummary(e.getVenue()), prices.get(e.getId()),
                        participants.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    private static Subquery<Integer> participantNamed(CriteriaBuilder cb, AbstractQuery<?> query, Root<Event> event,
                                                      String pattern) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<Event> correlated = subquery.correlate(event);
        Join<Event, Participant> participant = correlated.join("participants");
        return subquery.select(cb.literal(1)).where(like(cb, participant.get("name"), pattern));
    }

    private static Predicate like(CriteriaBuilder cb, Expression<String> field, String pattern) {
        return cb.like(cb.lower(field), pattern, LIKE_ESCAPE);
    }

    /**
     * The normalized filters of a request. Text filters are already {@code %lowercase%} patterns with {@code %},
     * {@code _} and the escape character escaped, so a user can't send a wildcard.
     */
    private record Filters(String text, String city, String participant, EventSearchRequest request, LocalDateTime now) {

        static Filters of(EventSearchRequest request, LocalDateTime now) {
            return new Filters(containsPattern(request.q()), blankToNull(request.city()),
                    containsPattern(request.participant()), request, now);
        }

        Predicate[] toPredicates(CriteriaBuilder cb, AbstractQuery<?> query, Root<Event> event) {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(event.get("status").in(VISIBLE));
            predicates.add(cb.greaterThan(event.get("dateTime"), now));
            if (request.type() != null) {
                predicates.add(cb.equal(event.get("type"), request.type()));
            }
            if (request.organizationId() != null) {
                predicates.add(cb.equal(event.get("organizationId"), request.organizationId()));
            }
            if (city != null) {
                // The collation of MySQL ignores accents: "cordoba" matches "Córdoba"
                predicates.add(cb.equal(cb.lower(event.get("venue").get("city")), city.toLowerCase()));
            }
            if (participant != null) {
                predicates.add(cb.exists(participantNamed(cb, query, event, participant)));
            }
            if (text != null) {
                predicates.add(cb.or(
                        like(cb, event.get("name"), text),
                        like(cb, event.get("description"), text),
                        cb.exists(participantNamed(cb, query, event, text))));
            }
            if (request.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(event.get("dateTime"), request.from().atStartOfDay()));
            }
            if (request.to() != null) {
                predicates.add(cb.lessThan(event.get("dateTime"), request.to().plusDays(1).atStartOfDay()));
            }
            return predicates.toArray(Predicate[]::new);
        }

        private static String containsPattern(String value) {
            String trimmed = blankToNull(value);
            if (trimmed == null) {
                return null;
            }
            String escaped = trimmed.toLowerCase()
                    .replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + String.valueOf(LIKE_ESCAPE))
                    .replace("%", LIKE_ESCAPE + "%")
                    .replace("_", LIKE_ESCAPE + "_");
            return "%" + escaped + "%";
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
