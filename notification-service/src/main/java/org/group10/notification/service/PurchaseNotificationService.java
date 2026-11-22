package org.group10.notification.service;

import org.group10.notification.entity.ProcessedMessage;
import org.group10.notification.repository.ProcessedMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Manda el mail de una compra una sola vez por {@code messageId} (US-NOT-01).
 * <p>
 * El {@code messageId} se inserta antes de enviar, en la misma transacción: si otra entrega del mismo mensaje está en
 * curso, el INSERT espera a que termine y falla por la clave ({@code DataIntegrityViolationException}). Si el envío
 * falla, el rollback borra la fila y el reintento lo vuelve a intentar. Riesgo aceptado: si el mail sale y el commit
 * falla, el reintento lo manda otra vez.
 */
@Service
public class PurchaseNotificationService {

    private final ProcessedMessageRepository processedMessages;
    private final PurchaseMailService purchaseMailService;

    public PurchaseNotificationService(ProcessedMessageRepository processedMessages,
                                       PurchaseMailService purchaseMailService) {
        this.processedMessages = processedMessages;
        this.purchaseMailService = purchaseMailService;
    }

    @Transactional
    public void notifyPurchase(UUID messageId, Long orderId, Long userId) {
        processedMessages.saveAndFlush(new ProcessedMessage(messageId, Instant.now()));
        purchaseMailService.send(orderId, userId);
    }
}
