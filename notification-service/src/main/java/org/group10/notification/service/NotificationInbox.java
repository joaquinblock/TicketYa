package org.group10.notification.service;

import lombok.RequiredArgsConstructor;
import org.group10.notification.dto.NotificationResponse;
import org.group10.notification.entity.Notification;
import org.group10.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** La bandeja de cada usuario: lo que se le avisó, de lo más nuevo a lo más viejo. */
@Service
@RequiredArgsConstructor
public class NotificationInbox {

    private final NotificationRepository notifications;
    private final Clock clock;

    @Transactional
    public void record(Long userId, String title, String body) {
        notifications.save(new Notification(userId, title, body, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findFor(Long userId) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.findByUserIdAndReadAtIsNull(userId).forEach(notification -> notification.markRead(clock.instant()));
    }
}
