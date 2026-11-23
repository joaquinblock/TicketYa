package org.group10.ticket.service;

import org.group10.common.exception.TicketYaException;
import org.group10.ticket.dto.CheckInResponse;
import org.group10.ticket.entity.Order;
import org.group10.ticket.entity.Ticket;
import org.group10.ticket.entity.TicketStatus;
import org.group10.ticket.exception.TicketErrorCode;
import org.group10.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.slf4j.MDC;
import org.group10.common.tracing.CorrelationHeaders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class CheckInServiceTest {

    private static final Long STAFF_ID = 4L;
    private static final Long EVENT_ID = 1L;
    private static final String QR = "859c3438-0059-403e-af24-f76673ca852e";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 13, 20, 41);

    @Mock
    private TicketRepository ticketRepository;

    private CheckInService service;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");
        Clock clock = Clock.fixed(NOW.atZone(zone).toInstant(), zone);
        service = new CheckInService(ticketRepository, clock);
    }

    @Test
    void checkIn_issuedTicketOfTheEvent_marksItUsedByTheStaffAndReturnsItsPlace() {
        when(ticketRepository.findWithOrderByQrCode(QR)).thenReturn(Optional.of(ticket(EVENT_ID, TicketStatus.ISSUED)));
        when(ticketRepository.markIssuedTicketAsUsed(10L, NOW, STAFF_ID)).thenReturn(1);

        CheckInResponse response = service.checkIn(STAFF_ID, EVENT_ID, QR);

        assertThat(response).isEqualTo(new CheckInResponse(10L, "Platea", "A", 15, NOW));
        verify(ticketRepository).markIssuedTicketAsUsed(10L, NOW, STAFF_ID);
    }

    @Test
    void checkIn_success_logsCriticalOperationWithoutQr(CapturedOutput output) {
        MDC.put(CorrelationHeaders.MDC_KEY, "operation-123");
        when(ticketRepository.findWithOrderByQrCode(QR)).thenReturn(Optional.of(ticket(EVENT_ID, TicketStatus.ISSUED)));
        when(ticketRepository.markIssuedTicketAsUsed(10L, NOW, STAFF_ID)).thenReturn(1);

        service.checkIn(STAFF_ID, EVENT_ID, QR);

        assertThat(output).contains("operation=check_in")
                .contains("status=success")
                .contains("correlationId=operation-123")
                .doesNotContain(QR);
        MDC.clear();
    }

    @Test
    void checkIn_unknownQr_isInvalid() {
        when(ticketRepository.findWithOrderByQrCode(QR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn(STAFF_ID, EVENT_ID, QR))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(TicketErrorCode.INVALID_QR);
        verifyNothingMarked();
    }

    @Test
    void checkIn_qrOfAnotherEvent_isInvalidLikeAnUnknownOne() {
        when(ticketRepository.findWithOrderByQrCode(QR)).thenReturn(Optional.of(ticket(2L, TicketStatus.ISSUED)));

        assertThatThrownBy(() -> service.checkIn(STAFF_ID, EVENT_ID, QR))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(TicketErrorCode.INVALID_QR);
        verifyNothingMarked();
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"VOIDED", "REFUNDED", "RESERVED"})
    void checkIn_ticketThatCannotEnter_isInvalid(TicketStatus status) {
        when(ticketRepository.findWithOrderByQrCode(QR)).thenReturn(Optional.of(ticket(EVENT_ID, status)));

        assertThatThrownBy(() -> service.checkIn(STAFF_ID, EVENT_ID, QR))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(TicketErrorCode.INVALID_QR);
        verifyNothingMarked();
    }

    @Test
    void checkIn_usedTicket_isAlreadyUsed() {
        when(ticketRepository.findWithOrderByQrCode(QR)).thenReturn(Optional.of(ticket(EVENT_ID, TicketStatus.USED)));

        assertThatThrownBy(() -> service.checkIn(STAFF_ID, EVENT_ID, QR))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(TicketErrorCode.TICKET_ALREADY_USED);
        verifyNothingMarked();
    }

    @Test
    void checkIn_anotherScanMarkedItFirst_isAlreadyUsed() {
        when(ticketRepository.findWithOrderByQrCode(QR)).thenReturn(Optional.of(ticket(EVENT_ID, TicketStatus.ISSUED)));
        when(ticketRepository.markIssuedTicketAsUsed(10L, NOW, STAFF_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.checkIn(STAFF_ID, EVENT_ID, QR))
                .isInstanceOf(TicketYaException.class)
                .extracting("errorCode").isEqualTo(TicketErrorCode.TICKET_ALREADY_USED);
    }

    private void verifyNothingMarked() {
        verify(ticketRepository, never()).markIssuedTicketAsUsed(anyLong(), any(), anyLong());
    }

    private static Ticket ticket(Long eventId, TicketStatus status) {
        Order order = Order.builder().id(20L).userId(1L).eventId(eventId).build();
        return Ticket.builder()
                .id(10L)
                .order(order)
                .eventSectorId(2L)
                .sectorName("Platea")
                .seatId(1L)
                .row("A")
                .number(15)
                .price(new BigDecimal("70000.00"))
                .qrCode(QR)
                .status(status)
                .build();
    }
}
