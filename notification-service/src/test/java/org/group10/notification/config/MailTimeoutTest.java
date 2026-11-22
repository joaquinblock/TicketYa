package org.group10.notification.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Un SMTP que acepta la conexión y no contesta tiene que terminar en {@link MailException}, así el mensaje se
 * reintenta y llega a la DLQ (US-NOT-01, CA3), en vez de dejar colgado el listener con la transacción abierta.
 * Usa el {@code JavaMailSender} real con la configuración de {@code application.yml}.
 */
@SpringBootTest(classes = MailSenderAutoConfiguration.class)
class MailTimeoutTest {

    private static final ServerSocket silentSmtp = openSilentServer();
    private static final List<Socket> accepted = new ArrayList<>();

    @Autowired
    private JavaMailSender mailSender;

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", silentSmtp::getLocalPort);
    }

    @AfterAll
    static void closeServer() throws IOException {
        for (Socket socket : accepted) {
            socket.close();
        }
        silentSmtp.close();
    }

    @Test
    void smtpThatNeverAnswersFailsWithinSeconds() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@ticketya.com");
        message.setTo("client@ticketya.com");
        message.setSubject("Tu compra");
        message.setText("Hola");

        assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                assertThatThrownBy(() -> mailSender.send(message)).isInstanceOf(MailException.class));
    }

    /** Acepta las conexiones y nunca escribe el saludo SMTP. */
    private static ServerSocket openSilentServer() {
        try {
            ServerSocket server = new ServerSocket(0);
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        synchronized (accepted) {
                            accepted.add(socket);
                        }
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
