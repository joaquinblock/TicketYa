package org.group10.notification.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.notification.entity.Notification;
import org.group10.notification.repository.NotificationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Avisos de muestra para el cliente del seeder (user 1): uno sin leer y uno leído. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSeeder implements ApplicationRunner {

    private static final Long CLIENT_USER_ID = 1L;

    private final NotificationRepository notifications;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (notifications.count() > 0) {
            log.info("NotificationSeeder: ya hay datos, se omite la carga");
            return;
        }
        Instant now = clock.instant();
        Notification welcome = new Notification(CLIENT_USER_ID, "Te damos la bienvenida a TicketYa",
                "Elegí tus artistas favoritos y tu ciudad en Preferencias: te avisamos cuando tengan un evento.",
                now.minus(Duration.ofDays(3)));
        welcome.markRead(now.minus(Duration.ofDays(2)));
        Notification purchase = new Notification(CLIENT_USER_ID, "Tu compra está confirmada",
                "Tus entradas ya están en Mis entradas, con su código QR para el ingreso.", now.minus(Duration.ofHours(2)));
        notifications.saveAll(List.of(welcome, purchase));
        log.info("NotificationSeeder: 2 avisos para el usuario {}", CLIENT_USER_ID);
    }
}
