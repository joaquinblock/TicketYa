package org.group10.ticket.service;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.dto.EventReportEventResponse;
import org.group10.ticket.dto.EventReportResponse;
import org.group10.ticket.dto.EventReportResponse.EventTotals;
import org.group10.ticket.dto.EventReportResponse.SectorReport;
import org.group10.ticket.entity.EventSectorPrice;
import org.group10.ticket.repository.EventSectorPriceRepository;
import org.group10.ticket.repository.EventSectorReportAggregate;
import org.group10.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventReportService {

    private static final BigDecimal ZERO_DECIMAL = BigDecimal.ZERO.setScale(2);

    private final EventClient eventClient;
    private final EventSectorPriceRepository eventSectorPriceRepository;
    private final TicketRepository ticketRepository;

    public EventReportResponse findReport(Caller caller, Long eventId) {
        EventReportEventResponse event = eventClient.findEventForReport(eventId);
        ensureCallerOwnsEvent(caller, event, eventId);

        Map<String, Integer> capacitiesBySectorName = event.sectors().stream()
                .collect(Collectors.toMap(EventReportEventResponse.Sector::name,
                        EventReportEventResponse.Sector::capacity));
        List<EventSectorPrice> sectors = eventSectorPriceRepository.findByEventIdOrderByEventSectorId(eventId);
        Map<Long, EventSectorReportAggregate> aggregatesBySector = ticketRepository
                .findReportAggregatesByEventId(eventId).stream()
                .collect(Collectors.toMap(EventSectorReportAggregate::eventSectorId, Function.identity()));

        List<SectorReport> sectorReports = sectors.stream()
                .map(sector -> reportOf(sector, capacitiesBySectorName, aggregatesBySector))
                .toList();
        return new EventReportResponse(eventId, sectorReports, totalOf(sectorReports));
    }

    private static SectorReport reportOf(EventSectorPrice sector, Map<String, Integer> capacitiesBySectorName,
                                         Map<Long, EventSectorReportAggregate> aggregatesBySector) {
        Integer capacity = capacitiesBySectorName.get(sector.getSectorName());
        if (capacity == null) {
            throw new TicketYaException(CommonErrorCode.INTERNAL_ERROR,
                    "No se encontró la capacidad del sector %d del evento %d"
                            .formatted(sector.getEventSectorId(), sector.getEventId()));
        }

        EventSectorReportAggregate aggregate = aggregatesBySector.get(sector.getEventSectorId());
        long sold = aggregate == null ? 0 : aggregate.soldTickets();
        long returned = aggregate == null ? 0 : aggregate.refundedTickets();
        BigDecimal revenue = aggregate == null || aggregate.revenue() == null ? ZERO_DECIMAL : aggregate.revenue();
        return new SectorReport(sector.getEventSectorId(), sector.getSectorName(), capacity, sold, returned,
                occupancy(sold, capacity), revenue);
    }

    private static EventTotals totalOf(List<SectorReport> sectors) {
        long capacity = sectors.stream().mapToLong(SectorReport::capacity).sum();
        long sold = sectors.stream().mapToLong(SectorReport::soldTickets).sum();
        long returned = sectors.stream().mapToLong(SectorReport::returnedTickets).sum();
        BigDecimal revenue = sectors.stream().map(SectorReport::revenue).reduce(ZERO_DECIMAL, BigDecimal::add);
        return new EventTotals(capacity, sold, returned, occupancy(sold, capacity), revenue);
    }

    private static BigDecimal occupancy(long sold, long capacity) {
        if (capacity == 0) {
            return ZERO_DECIMAL;
        }
        return BigDecimal.valueOf(sold).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
    }

    private static void ensureCallerOwnsEvent(Caller caller, EventReportEventResponse event, Long eventId) {
        if (!Objects.equals(caller.organizationId(), event.organizationId())) {
            throw new TicketYaException(CommonErrorCode.FORBIDDEN,
                    "El usuario %d no pertenece a la organización del evento %d".formatted(caller.userId(), eventId));
        }
    }
}
