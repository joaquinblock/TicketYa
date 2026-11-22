package org.group10.notification.service;

import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.group10.notification.messaging.EventChangedMessage.Change;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Arma el aviso de que un evento se canceló, se suspendió o cambió de fecha (US-NOT-02). Es texto plano: no lleva QR
 * ni tablas. No envía nada.
 */
@Component
public class EventChangeMailComposer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    public SimpleMailMessage compose(EventDetail event, Change change, UserContact user) {
        String greeting = user.firstName() == null ? "Hola," : "Hola, " + user.firstName() + ":";
        String place = placeOf(event.venue());
        String subject;
        String body;
        switch (change) {
            case CANCELLED -> {
                subject = "Se canceló " + event.name();
                body = """
                        El evento %s, que era el %s en %s, se canceló.

                        Tus entradas pasan a reintegro: te devolvemos lo que pagaste por el mismo medio de pago, \
                        sin que tengas que hacer nada."""
                        .formatted(event.name(), event.dateTime().format(DATE_TIME), place);
            }
            case SUSPENDED -> {
                subject = "Se suspendió " + event.name();
                body = """
                        El evento %s, del %s en %s, quedó suspendido.

                        Tus entradas siguen siendo tuyas. Te avisamos cuando tenga una fecha nueva."""
                        .formatted(event.name(), event.dateTime().format(DATE_TIME), place);
            }
            case RESCHEDULED -> {
                subject = "Cambió la fecha de " + event.name();
                body = """
                        El evento %s tiene una fecha nueva: el %s en %s.

                        Tus entradas siguen valiendo para la fecha nueva."""
                        .formatted(event.name(), event.dateTime().format(DATE_TIME), place);
            }
            default -> throw new IllegalArgumentException("Cambio sin aviso: " + change);
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(user.email());
        mail.setSubject(subject);
        mail.setText(greeting + "\n\n" + body + "\n\nTicketYa\n");
        return mail;
    }

    private static String placeOf(EventDetail.Venue venue) {
        return venue == null ? "el lugar del evento" : "%s, %s, %s".formatted(venue.name(), venue.address(), venue.city());
    }
}
