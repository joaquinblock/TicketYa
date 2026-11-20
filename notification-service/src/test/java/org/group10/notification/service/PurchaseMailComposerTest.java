package org.group10.notification.service;

import org.group10.notification.dto.PaidOrder;
import org.group10.notification.dto.UserContact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseMailComposerTest {

    private final PurchaseMailComposer composer = new PurchaseMailComposer();

    @Test
    void compose_numberedSeats_listsEachTicketWithItsRowSeatAndQr() {
        PaidOrder order = order("Los Serranos en vivo", List.of(
                new PaidOrder.Ticket(1L, "Platea", "A", 15, new BigDecimal("70000.00"), "qr-uno"),
                new PaidOrder.Ticket(2L, "Platea", "A", 16, new BigDecimal("70000.00"), "qr-dos")));

        PurchaseMailComposer.Mail mail = composer.compose(order, user("Juan"));

        assertThat(mail.subject()).isEqualTo("Tus entradas para Los Serranos en vivo");
        assertThat(mail.html())
                .contains("¡Gracias por tu compra, Juan!")
                .contains("<strong>Los Serranos en vivo</strong>")
                .contains("13/12/2026 a las 21:00")
                .contains("Estadio del Sur, Av. del Estadio 1000, Buenos Aires")
                .contains("Fila A, asiento 15").contains("Fila A, asiento 16")
                .contains("<code>qr-uno</code>").contains("<code>qr-dos</code>")
                .contains("$ 70.000,00")
                .contains("Total: $ 140.000,00");
    }

    @Test
    void compose_generalAdmission_saysTheTicketIsNotNumbered() {
        PaidOrder order = order("Festival de Verano", List.of(
                new PaidOrder.Ticket(1L, "Campo", null, null, new BigDecimal("70000.00"), "qr-campo")));

        PurchaseMailComposer.Mail mail = composer.compose(order, user("Juan"));

        assertThat(mail.html()).contains("<td>Campo</td><td>Sin numerar</td>").doesNotContain("Fila");
    }

    @Test
    void compose_ticketWithoutQr_saysItIsNotAvailable() {
        PaidOrder order = order("Festival de Verano", List.of(
                new PaidOrder.Ticket(1L, "Campo", null, null, new BigDecimal("70000.00"), null)));

        assertThat(composer.compose(order, user("Juan")).html()).contains("No disponible").doesNotContain("<code>");
    }

    @Test
    void compose_userWithoutName_greetsWithoutIt() {
        PaidOrder order = order("Festival de Verano", List.of(
                new PaidOrder.Ticket(1L, "Campo", null, null, new BigDecimal("70000.00"), "qr-campo")));

        assertThat(composer.compose(order, user(null)).html()).contains("<h2>¡Gracias por tu compra!</h2>");
    }

    @Test
    void compose_htmlInTheData_isEscaped() {
        PaidOrder order = order("<script>alert(1)</script>", List.of(
                new PaidOrder.Ticket(1L, "Campo & Platea", null, null, new BigDecimal("70000.00"), "qr-campo")));

        PurchaseMailComposer.Mail mail = composer.compose(order, user("<b>Juan</b>"));

        assertThat(mail.html())
                .doesNotContain("<script>").doesNotContain("<b>Juan</b>")
                .contains("&lt;script&gt;").contains("Campo &amp; Platea");
    }

    private static PaidOrder order(String eventName, List<PaidOrder.Ticket> tickets) {
        PaidOrder.Event event = new PaidOrder.Event(eventName, LocalDateTime.of(2026, 12, 13, 21, 0),
                "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires");
        BigDecimal total = tickets.stream().map(PaidOrder.Ticket::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaidOrder(10L, event, total, tickets);
    }

    private static UserContact user(String firstName) {
        return new UserContact(1L, "client@ticketya.com", firstName, "Pérez");
    }
}
