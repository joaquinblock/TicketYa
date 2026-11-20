package org.group10.notification.service;

import org.group10.notification.dto.PaidOrder;
import org.group10.notification.dto.UserContact;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Arma el mail de una compra confirmada: el evento, cuándo y dónde es, y cada entrada con su ubicación y su QR.
 * No envía nada. Todo lo que viene de otros servicios se escapa, porque el cuerpo es HTML.
 * <p>
 * Cada QR va como imagen embebida ({@code cid:}), no como {@code data:} en el HTML, porque Gmail y otros clientes
 * bloquean esas. Debajo va el código en texto, por si el cliente no muestra imágenes o el lector del ingreso falla.
 */
@Component
public class PurchaseMailComposer {

    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");
    private static final String NO_QR = "No disponible";

    /**
     * @param qrs las imágenes que hay que adjuntar, una por entrada con QR, en el orden de las entradas
     */
    public record Mail(String subject, String html, List<InlineQr> qrs) {
    }

    /**
     * Una imagen embebida: el HTML la referencia como {@code cid:<contentId>}.
     */
    public record InlineQr(String contentId, String qrCode) {
    }

    public Mail compose(PaidOrder order, UserContact user) {
        PaidOrder.Event event = order.event();
        String subject = "Tus entradas para " + event.name();
        String greeting = user.firstName() == null ? "¡Gracias por tu compra!"
                : "¡Gracias por tu compra, " + escape(user.firstName()) + "!";
        String rows = order.tickets().stream().map(PurchaseMailComposer::row).collect(Collectors.joining());

        String html = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #222222;">
                <h2>%s</h2>
                <p>Estas son tus entradas para <strong>%s</strong>.</p>
                <p>Fecha: %s<br>Lugar: %s, %s, %s</p>
                <table cellpadding="6" style="border-collapse: collapse;">
                <tr><th align="left">Sector</th><th align="left">Ubicación</th><th align="left">Precio</th><th align="left">Código QR</th></tr>
                %s</table>
                <p>Total: %s</p>
                <p>Presentá el código QR de cada entrada en el ingreso.</p>
                </body>
                </html>
                """.formatted(greeting, escape(event.name()), event.dateTime().format(DATE_TIME),
                escape(event.venueName()), escape(event.venueAddress()), escape(event.venueCity()), rows,
                money(order.total()));
        List<InlineQr> qrs = order.tickets().stream()
                .filter(ticket -> ticket.qrCode() != null)
                .map(ticket -> new InlineQr(contentId(ticket), ticket.qrCode()))
                .toList();
        return new Mail(subject, html, qrs);
    }

    private static String row(PaidOrder.Ticket ticket) {
        String place = ticket.row() == null ? "Sin numerar"
                : "Fila " + escape(ticket.row()) + ", asiento " + ticket.number();
        String qr = ticket.qrCode() == null ? NO_QR
                : "<img src=\"cid:%s\" width=\"160\" height=\"160\" alt=\"QR de la entrada\"><br><code>%s</code>"
                .formatted(contentId(ticket), escape(ticket.qrCode()));
        return "<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>%n"
                .formatted(escape(ticket.sectorName()), place, money(ticket.price()), qr);
    }

    private static String contentId(PaidOrder.Ticket ticket) {
        return "qr-" + ticket.id();
    }

    private static String money(BigDecimal amount) {
        return "$ " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ARGENTINA)).format(amount);
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
