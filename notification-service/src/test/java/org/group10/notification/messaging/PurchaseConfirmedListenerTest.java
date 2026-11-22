package org.group10.notification.messaging;

import org.group10.notification.service.PurchaseNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PurchaseConfirmedListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7f1c2e3a-0b4d-4c5e-8f6a-1b2c3d4e5f60");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-21T18:00:00Z");

    @Mock
    private PurchaseNotificationService notificationService;

    private PurchaseConfirmedListener listener;

    @BeforeEach
    void setUp() {
        listener = new PurchaseConfirmedListener(notificationService);
    }

    @Test
    void validMessageIsDelegatedWithItsIds() {
        listener.onPurchaseConfirmed(new PurchaseConfirmedMessage(MESSAGE_ID, OCCURRED_AT, 42L, 7L, 3L));

        verify(notificationService).notifyPurchase(MESSAGE_ID, 42L, 7L);
    }

    @Test
    void messageWithoutOrderIdIsRejectedWithoutRetry() {
        PurchaseConfirmedMessage message = new PurchaseConfirmedMessage(MESSAGE_ID, OCCURRED_AT, null, 7L, 3L);

        assertThatThrownBy(() -> listener.onPurchaseConfirmed(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    void messageWithoutUserIdIsRejectedWithoutRetry() {
        PurchaseConfirmedMessage message = new PurchaseConfirmedMessage(MESSAGE_ID, OCCURRED_AT, 42L, null, 3L);

        assertThatThrownBy(() -> listener.onPurchaseConfirmed(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    void messageWithoutMessageIdIsRejectedWithoutRetry() {
        PurchaseConfirmedMessage message = new PurchaseConfirmedMessage(null, OCCURRED_AT, 42L, 7L, 3L);

        assertThatThrownBy(() -> listener.onPurchaseConfirmed(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(notificationService);
    }

    @Test
    void repeatedMessageIsAcknowledgedWithoutError() {
        doThrow(new DataIntegrityViolationException("Duplicate entry"))
                .when(notificationService).notifyPurchase(MESSAGE_ID, 42L, 7L);

        assertThatCode(() -> listener.onPurchaseConfirmed(new PurchaseConfirmedMessage(MESSAGE_ID, OCCURRED_AT, 42L, 7L, 3L)))
                .doesNotThrowAnyException();
    }
}
