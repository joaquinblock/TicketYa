package org.group10.notification.service;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.group10.notification.client.AuthClient;
import org.group10.notification.client.TicketClient;
import org.group10.notification.dto.PaidOrder;
import org.group10.notification.dto.UserContact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseMailServiceTest {

    @Mock
    private TicketClient ticketClient;

    @Mock
    private AuthClient authClient;

    @Mock
    private JavaMailSender mailSender;

    private PurchaseMailService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseMailService(ticketClient, authClient, new PurchaseMailComposer(), mailSender,
                "no-reply@ticketya.com");
    }

    @Test
    void send_paidOrderAndActiveUser_sendsAnHtmlMailToTheBuyer() throws Exception {
        when(ticketClient.findPaidOrder(10L)).thenReturn(paidOrder());
        when(authClient.findContact(1L)).thenReturn(new UserContact(1L, "client@ticketya.com", "Juan", "Pérez"));

        service.send(10L, 1L);

        MimeMessage message = sentMessage();
        assertThat(message.getAllRecipients()).containsExactly(new InternetAddress("client@ticketya.com"));
        assertThat(message.getFrom()).containsExactly(new InternetAddress("no-reply@ticketya.com"));
        assertThat(message.getSubject()).isEqualTo("Tus entradas para Los Serranos en vivo");
        assertThat(message.getContentType()).startsWith("text/html");
        assertThat((String) message.getContent()).contains("¡Gracias por tu compra, Juan!").contains("qr-uno");
    }

    @Test
    void send_orderNotFound_sendsNothing() {
        when(ticketClient.findPaidOrder(10L)).thenThrow(clientError(HttpStatus.NOT_FOUND));

        service.send(10L, 1L);

        verifyNoInteractions(authClient, mailSender);
    }

    @Test
    void send_orderNotPaid_sendsNothing() {
        when(ticketClient.findPaidOrder(10L)).thenThrow(clientError(HttpStatus.CONFLICT));

        service.send(10L, 1L);

        verifyNoInteractions(authClient, mailSender);
    }

    @Test
    void send_userDeleted_sendsNothing() {
        when(ticketClient.findPaidOrder(10L)).thenReturn(paidOrder());
        when(authClient.findContact(1L)).thenThrow(clientError(HttpStatus.NOT_FOUND));

        service.send(10L, 1L);

        verifyNoInteractions(mailSender);
    }

    @Test
    void send_ticketServiceFails_propagatesSoTheCallerCanRetry() {
        when(ticketClient.findPaidOrder(10L)).thenThrow(
                HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", HttpHeaders.EMPTY,
                        null, null));

        assertThatThrownBy(() -> service.send(10L, 1L)).isInstanceOf(HttpServerErrorException.class);
        verifyNoInteractions(mailSender);
    }

    @Test
    void send_authServiceDoesNotAnswer_propagatesSoTheCallerCanRetry() {
        when(ticketClient.findPaidOrder(10L)).thenReturn(paidOrder());
        when(authClient.findContact(1L)).thenThrow(new ResourceAccessException("Read timed out"));

        assertThatThrownBy(() -> service.send(10L, 1L)).isInstanceOf(ResourceAccessException.class);
        verifyNoInteractions(mailSender);
    }

    @Test
    void send_smtpFails_propagatesSoTheCallerCanRetry() {
        when(ticketClient.findPaidOrder(10L)).thenReturn(paidOrder());
        when(authClient.findContact(1L)).thenReturn(new UserContact(1L, "client@ticketya.com", "Juan", "Pérez"));
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessagePreparator.class));

        assertThatThrownBy(() -> service.send(10L, 1L)).isInstanceOf(MailSendException.class);
    }

    private MimeMessage sentMessage() throws Exception {
        ArgumentCaptor<MimeMessagePreparator> preparator = ArgumentCaptor.forClass(MimeMessagePreparator.class);
        verify(mailSender).send(preparator.capture());
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        preparator.getValue().prepare(message);
        message.saveChanges();
        return message;
    }

    private static HttpClientErrorException clientError(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, null, null);
    }

    private static PaidOrder paidOrder() {
        PaidOrder.Event event = new PaidOrder.Event("Los Serranos en vivo", LocalDateTime.of(2026, 12, 13, 21, 0),
                "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires");
        return new PaidOrder(10L, event, new BigDecimal("70000.00"),
                List.of(new PaidOrder.Ticket(1L, "Platea", "A", 15, new BigDecimal("70000.00"), "qr-uno")));
    }
}
