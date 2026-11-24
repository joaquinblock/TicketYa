package org.group10.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Un aviso de la bandeja del usuario. Se guarda junto con cada mail que sale (compra, cambio de un evento, artista
 * favorito) para que el front lo muestre en Notificaciones.
 */
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notification_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    public static final int TITLE_MAX_LENGTH = 150;
    public static final int BODY_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = BODY_MAX_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    public Notification(Long userId, String title, String body, Instant createdAt) {
        this.userId = userId;
        this.title = truncate(title, TITLE_MAX_LENGTH);
        this.body = truncate(body, BODY_MAX_LENGTH);
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    private static String truncate(String text, int maxLength) {
        String trimmed = text.strip();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength - 1) + "…";
    }
}
