package org.group10.notification.service;

import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Arma el aviso de que un artista favorito tiene un evento nuevo en la ciudad del cliente (US-NOT-03). Es texto plano.
 * No envía nada.
 */
@Component
public class FavoriteEventMailComposer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    public SimpleMailMessage compose(EventDetail event, UserContact user) {
        String greeting = user.firstName() == null ? "Hola," : "Hola, " + user.firstName() + ":";
        EventDetail.Venue venue = event.venue();
        String participants = event.participants().stream()
                .map(EventDetail.Participant::name)
                .collect(Collectors.joining(", "));
        String body = """
                Hay un evento nuevo de un artista que te gusta: %s, con %s.

                Es el %s en %s, %s, %s. Las entradas ya están a la venta en TicketYa."""
                .formatted(event.name(), participants, event.dateTime().format(DATE_TIME), venue.name(),
                        venue.address(), venue.city());

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(user.email());
        mail.setSubject("%s en %s".formatted(event.name(), venue.city()));
        mail.setText(greeting + "\n\n" + body + "\n\nTicketYa\n");
        return mail;
    }
}
