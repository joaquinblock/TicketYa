package org.group10.notification.service;

import org.group10.notification.client.AuthClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.PaidOrder;
import org.group10.notification.dto.UserContact;
import org.group10.notification.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Idempotencia de la confirmación de compra contra MySQL real: la clave del {@code messageId} es la que impide que
 * dos entregas del mismo mensaje manden dos mails (US-NOT-01).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@ActiveProfiles("test")
class PurchaseNotificationMySqlIntegrationTest {

    private static final long ORDER_ID = 42L;
    private static final long USER_ID = 7L;

    @Autowired
    private PurchaseNotificationService notificationService;

    @Autowired
    private ProcessedMessageRepository processedMessages;

    @MockitoBean
    private TicketClient ticketClient;

    @MockitoBean
    private AuthClient authClient;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        processedMessages.deleteAll();
        when(ticketClient.findPaidOrder(ORDER_ID)).thenReturn(paidOrder());
        when(authClient.findContact(USER_ID)).thenReturn(new UserContact(USER_ID, "client@ticketya.com", "Juan", "Pérez"));
    }

    @Test
    void newMessageSendsTheMailAndIsMarkedAsProcessed() {
        UUID messageId = UUID.randomUUID();

        notificationService.notifyPurchase(messageId, ORDER_ID, USER_ID);

        verify(mailSender).send(any(MimeMessagePreparator.class));
        assertThat(processedMessages.existsById(messageId)).isTrue();
    }

    @Test
    void sameMessageTwiceSendsASingleMail() {
        UUID messageId = UUID.randomUUID();
        notificationService.notifyPurchase(messageId, ORDER_ID, USER_ID);

        assertThatThrownBy(() -> notificationService.notifyPurchase(messageId, ORDER_ID, USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(mailSender, times(1)).send(any(MimeMessagePreparator.class));
    }

    @Test
    void sameMessageInTwoThreadsAtOnceSendsASingleMail() throws Exception {
        UUID messageId = UUID.randomUUID();
        // El envío tarda: la transacción del primero sigue abierta cuando el segundo intenta insertar
        doAnswer(invocation -> {
            Thread.sleep(500);
            return null;
        }).when(mailSender).send(any(MimeMessagePreparator.class));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        notificationService.notifyPurchase(messageId, ORDER_ID, USER_ID);
                        return null;
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            start.countDown();

            List<Throwable> errors = new ArrayList<>();
            for (Future<Throwable> result : results) {
                errors.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(errors).filteredOn(e -> e == null).hasSize(1);
            assertThat(errors).filteredOn(e -> e != null).singleElement()
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            executor.shutdownNow();
        }
        verify(mailSender, times(1)).send(any(MimeMessagePreparator.class));
        assertThat(processedMessages.existsById(messageId)).isTrue();
    }

    @Test
    void failedSendIsNotProcessedAndTheNextAttemptSendsTheMail() {
        UUID messageId = UUID.randomUUID();
        doThrow(new MailSendException("SMTP caído"))
                .doNothing()
                .when(mailSender).send(any(MimeMessagePreparator.class));

        assertThatThrownBy(() -> notificationService.notifyPurchase(messageId, ORDER_ID, USER_ID))
                .isInstanceOf(MailSendException.class);
        assertThat(processedMessages.existsById(messageId)).isFalse();

        notificationService.notifyPurchase(messageId, ORDER_ID, USER_ID);

        verify(mailSender, times(2)).send(any(MimeMessagePreparator.class));
        assertThat(processedMessages.existsById(messageId)).isTrue();
    }

    @Test
    void orderNotPaidSendsNothingAndIsMarkedAsProcessed() {
        UUID messageId = UUID.randomUUID();
        when(ticketClient.findPaidOrder(ORDER_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, null, null));

        notificationService.notifyPurchase(messageId, ORDER_ID, USER_ID);

        verify(mailSender, never()).send(any(MimeMessagePreparator.class));
        assertThat(processedMessages.existsById(messageId)).isTrue();
    }

    @Test
    void deletedUserSendsNothingAndIsMarkedAsProcessed() {
        UUID messageId = UUID.randomUUID();
        when(authClient.findContact(USER_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

        notificationService.notifyPurchase(messageId, ORDER_ID, USER_ID);

        verify(mailSender, never()).send(any(MimeMessagePreparator.class));
        assertThat(processedMessages.existsById(messageId)).isTrue();
    }

    private static PaidOrder paidOrder() {
        PaidOrder.Event event = new PaidOrder.Event("Los Serranos en vivo", LocalDateTime.of(2026, 12, 13, 21, 0),
                "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires");
        return new PaidOrder(ORDER_ID, event, new BigDecimal("140000.00"), List.of(
                new PaidOrder.Ticket(1L, "Platea", "A", 15, new BigDecimal("70000.00"), "qr-uno")));
    }
}
