package org.group10.notification.messaging;

import org.group10.notification.messaging.EventChangedMessage.Change;
import org.group10.notification.service.EventChangeNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Una publicación también llega a {@code notification.event} por {@code event.*}: a los compradores no se les avisa
 * nada (US-NOT-03).
 */
@ExtendWith(MockitoExtension.class)
class EventChangedListenerPublicationTest {

    @Mock
    private EventChangeNotificationService notificationService;

    @InjectMocks
    private EventChangedListener listener;

    @Test
    void onEventChanged_publication_isDiscardedWithoutNotifyingTheBuyers() {
        listener.onEventChanged(new EventChangedMessage(UUID.randomUUID(), Instant.parse("2026-11-23T18:00:00Z"), 3L,
                Change.PUBLISHED));

        verifyNoInteractions(notificationService);
    }
}
