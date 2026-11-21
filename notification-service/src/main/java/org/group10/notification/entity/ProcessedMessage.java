package org.group10.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje de RabbitMQ ya procesado. La clave es el {@code messageId}: una segunda entrega del mismo mensaje no puede
 * insertarse y así no sale otro mail.
 * <p>
 * Implementa {@link Persistable} para que {@code saveAndFlush} haga un INSERT directo. Con un id asignado, Spring Data
 * haría un {@code merge} (SELECT y después INSERT), y dos entregas a la vez podrían pasar las dos el SELECT.
 */
@Entity
@Table(name = "processed_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessage implements Persistable<UUID> {

    @Id
    @Column(name = "message_id")
    private UUID id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Transient
    private boolean isNew = true;

    public ProcessedMessage(UUID id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }
}
