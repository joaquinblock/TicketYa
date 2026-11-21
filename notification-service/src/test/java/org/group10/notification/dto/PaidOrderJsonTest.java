package org.group10.notification.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que las respuestas reales del ticket-service y del auth-service se lean, con los campos que el mail no usa.
 */
class PaidOrderJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsTheOrderResponseOfTicketService() {
        PaidOrder order = mapper.readValue("""
                {"id": 10, "eventId": 1, "status": "PAID", "expiresAt": "2026-11-20T10:42:00", "total": 70000.00,
                 "paymentMethod": "CREDIT_CARD",
                 "event": {"name": "Los Serranos en vivo", "dateTime": "2026-12-13T21:00:00",
                           "venueName": "Estadio del Sur", "venueAddress": "Av. del Estadio 1000",
                           "venueCity": "Buenos Aires"},
                 "tickets": [{"id": 1, "eventSectorId": 2, "sectorName": "Platea", "seatId": 15, "row": "A",
                              "number": 15, "price": 70000.00, "status": "ISSUED", "qrCode": "qr-uno"}]}
                """, PaidOrder.class);

        assertThat(order.id()).isEqualTo(10L);
        assertThat(order.total()).isEqualByComparingTo(new BigDecimal("70000"));
        assertThat(order.event().dateTime()).isEqualTo(LocalDateTime.of(2026, 12, 13, 21, 0));
        assertThat(order.tickets()).singleElement()
                .satisfies(ticket -> {
                    assertThat(ticket.row()).isEqualTo("A");
                    assertThat(ticket.number()).isEqualTo(15);
                    assertThat(ticket.qrCode()).isEqualTo("qr-uno");
                });
    }

    @Test
    void readsTheContactOfAuthService() {
        UserContact user = mapper.readValue("""
                {"userId": 1, "email": "client@ticketya.com", "firstName": "Juan", "lastName": "Pérez"}
                """, UserContact.class);

        assertThat(user).isEqualTo(new UserContact(1L, "client@ticketya.com", "Juan", "Pérez"));
    }
}
