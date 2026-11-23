package org.group10.notification.messaging;

import org.group10.notification.service.FavoriteEventNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EventPublishedListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("6b7c8d9e-2f3a-4b4c-8d5e-6f7a8b9c0d1e");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-23T18:00:00Z");

    @Mock
    private FavoriteEventNotificationService notificationService;

    private EventPublishedListener listener;

    @BeforeEach
    void setUp() {
        listener = new EventPublishedListener(notificationService);
    }

    @Test
    void onEventPublished_validMessage_isDelegatedWithItsIds() {
        listener.onEventPublished(new EventPublishedMessage(MESSAGE_ID, OCCURRED_AT, 3L));

        verify(notificationService).notifyFans(MESSAGE_ID, 3L);
    }

    @Test
    void onEventPublished_withoutMessageId_isRejectedWithoutRetry() {
        assertRejected(new EventPublishedMessage(null, OCCURRED_AT, 3L));
    }

    @Test
    void onEventPublished_withoutEventId_isRejectedWithoutRetry() {
        assertRejected(new EventPublishedMessage(MESSAGE_ID, OCCURRED_AT, null));
    }

    private void assertRejected(EventPublishedMessage message) {
        assertThatThrownBy(() -> listener.onEventPublished(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }
}
