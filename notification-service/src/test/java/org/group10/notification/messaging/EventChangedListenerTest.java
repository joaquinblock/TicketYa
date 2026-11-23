package org.group10.notification.messaging;

import org.group10.notification.messaging.EventChangedMessage.Change;
import org.group10.notification.service.EventChangeNotificationService;
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
class EventChangedListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("5a6b7c8d-1e2f-4a3b-9c4d-5e6f7a8b9c0d");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-22T18:00:00Z");

    @Mock
    private EventChangeNotificationService notificationService;

    private EventChangedListener listener;

    @BeforeEach
    void setUp() {
        listener = new EventChangedListener(notificationService);
    }

    @Test
    void onEventChanged_validMessage_isDelegatedWithItsIdsAndChange() {
        listener.onEventChanged(new EventChangedMessage(MESSAGE_ID, OCCURRED_AT, 3L, Change.CANCELLED));

        verify(notificationService).notifyChange(MESSAGE_ID, 3L, Change.CANCELLED);
    }

    @Test
    void onEventChanged_withoutMessageId_isRejectedWithoutRetry() {
        assertRejected(new EventChangedMessage(null, OCCURRED_AT, 3L, Change.CANCELLED));
    }

    @Test
    void onEventChanged_withoutEventId_isRejectedWithoutRetry() {
        assertRejected(new EventChangedMessage(MESSAGE_ID, OCCURRED_AT, null, Change.SUSPENDED));
    }

    @Test
    void onEventChanged_withoutChange_isRejectedWithoutRetry() {
        assertRejected(new EventChangedMessage(MESSAGE_ID, OCCURRED_AT, 3L, null));
    }

    private void assertRejected(EventChangedMessage message) {
        assertThatThrownBy(() -> listener.onEventChanged(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }
}
