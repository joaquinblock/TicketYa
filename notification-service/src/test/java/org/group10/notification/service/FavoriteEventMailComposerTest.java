package org.group10.notification.service;

import org.group10.notification.dto.EventDetail;
import org.group10.notification.dto.UserContact;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteEventMailComposerTest {

    private static final EventDetail EVENT = new EventDetail(3L, "Los Serranos en vivo",
            LocalDateTime.of(2026, 12, 13, 21, 0),
            new EventDetail.Venue("Teatro de la Sierra", "Calle Pinto 650", "Tandil"), "PUBLISHED",
            List.of(new EventDetail.Participant(1L, "Los Serranos"), new EventDetail.Participant(4L, "La Banda del Sur")));

    private final FavoriteEventMailComposer composer = new FavoriteEventMailComposer();

    @Test
    void compose_publishedEvent_tellsWhoPlaysWhenAndWhere() {
        SimpleMailMessage mail = composer.compose(EVENT, new UserContact(7L, "fan@ticketya.com", "Juan", "Pérez"));

        assertThat(mail.getTo()).containsExactly("fan@ticketya.com");
        assertThat(mail.getSubject()).contains("Los Serranos en vivo", "Tandil");
        assertThat(mail.getText())
                .startsWith("Hola, Juan:")
                .contains("Los Serranos, La Banda del Sur", "13/12/2026 a las 21:00",
                        "Teatro de la Sierra, Calle Pinto 650, Tandil");
    }

    @Test
    void compose_userWithoutFirstName_greetsWithoutName() {
        SimpleMailMessage mail = composer.compose(EVENT, new UserContact(7L, "fan@ticketya.com", null, null));

        assertThat(mail.getText()).startsWith("Hola,\n");
    }
}
