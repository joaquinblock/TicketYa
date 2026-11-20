package org.group10.ticket.service;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.ticket.client.EventClient;
import org.group10.ticket.client.dto.EventReportEventResponse;
import org.group10.ticket.dto.EventReportResponse;
import org.group10.ticket.entity.EventSectorPrice;
import org.group10.ticket.repository.EventSectorPriceRepository;
import org.group10.ticket.repository.EventSectorReportAggregate;
import org.group10.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventReportServiceTest {

    private static final long EVENT_ID = 10L;
    private static final Caller ORGANIZER = new Caller(7L, "ORGANIZER", Set.of("REPORT_VIEW"), 3L);

    @Mock
    private EventClient eventClient;
    @Mock
    private EventSectorPriceRepository eventSectorPriceRepository;
    @Mock
    private TicketRepository ticketRepository;
    @InjectMocks
    private EventReportService eventReportService;

    @Test
    void findReport_aggregatesSectorMetricsAndIncludesSectorsWithoutSales() {
        when(eventClient.findEventForReport(EVENT_ID)).thenReturn(new EventReportEventResponse(3L, List.of(
                new EventReportEventResponse.Sector("Campo", 4),
                new EventReportEventResponse.Sector("Platea", 10))));
        when(eventSectorPriceRepository.findByEventIdOrderByEventSectorId(EVENT_ID)).thenReturn(List.of(
                sector(20L, "Campo"), sector(21L, "Platea")));
        when(ticketRepository.findReportAggregatesByEventId(EVENT_ID)).thenReturn(List.of(
                new EventSectorReportAggregate(20L, 2L, 1L, new BigDecimal("200.00"))));

        EventReportResponse report = eventReportService.findReport(ORGANIZER, EVENT_ID);

        assertThat(report.eventId()).isEqualTo(EVENT_ID);
        assertThat(report.sectors()).containsExactly(
                new EventReportResponse.SectorReport(20L, "Campo", 4, 2, 1,
                        new BigDecimal("50.00"), new BigDecimal("200.00")),
                new EventReportResponse.SectorReport(21L, "Platea", 10, 0, 0,
                        new BigDecimal("0.00"), new BigDecimal("0.00")));
        assertThat(report.total()).isEqualTo(new EventReportResponse.EventTotals(14, 2, 1,
                new BigDecimal("14.29"), new BigDecimal("200.00")));
    }

    @Test
    void findReport_forAnotherOrganization_throwsForbiddenBeforeReadingTicketData() {
        when(eventClient.findEventForReport(EVENT_ID))
                .thenReturn(new EventReportEventResponse(4L, List.of()));

        assertThatThrownBy(() -> eventReportService.findReport(ORGANIZER, EVENT_ID))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        verifyNoInteractions(eventSectorPriceRepository, ticketRepository);
    }

    private static EventSectorPrice sector(Long eventSectorId, String name) {
        return EventSectorPrice.builder()
                .eventSectorId(eventSectorId)
                .eventId(EVENT_ID)
                .sectorName(name)
                .price(new BigDecimal("100.00"))
                .build();
    }
}
