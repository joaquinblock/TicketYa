package org.group10.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo crítico se registra en ERROR, para encontrarlo en los logs de todo el sistema: lo que falla de nuestro lado (5xx).
 * Un error del cliente (4xx) es una respuesta esperada y queda en WARN.
 */
class ErrorLevelTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureHandlerLogs() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void stopCapturing() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @EnumSource(value = CommonErrorCode.class, names = {"SERVICE_UNAVAILABLE", "INTERNAL_ERROR"})
    void getLevel_serverError_isError(CommonErrorCode code) {
        assertThat(code.getLevel()).isEqualTo(org.slf4j.event.Level.ERROR);
    }

    @ParameterizedTest
    @EnumSource(value = CommonErrorCode.class, names = {"SERVICE_UNAVAILABLE", "INTERNAL_ERROR"},
            mode = EnumSource.Mode.EXCLUDE)
    void getLevel_clientError_isWarn(CommonErrorCode code) {
        assertThat(code.getLevel()).isEqualTo(org.slf4j.event.Level.WARN);
    }

    @Test
    void handleTicketYaException_serverError_logsError() {
        new GlobalExceptionHandler().handleTicketYaException(
                new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE, "payment-service no responde"));

        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("payment-service no responde");
        });
    }

    @Test
    void handleTicketYaException_clientError_logsWarn() {
        new GlobalExceptionHandler().handleTicketYaException(
                new TicketYaException(CommonErrorCode.RESOURCE_NOT_FOUND, "No se encontró el evento"));

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void handleTicketYaException_codeThatOverridesItsLevel_logsThatLevel() {
        ErrorCode critical409 = new ErrorCode() {
            @Override
            public HttpStatus getStatus() {
                return HttpStatus.CONFLICT;
            }

            @Override
            public String name() {
                return "CHARGED_BUT_NOT_ISSUED";
            }

            @Override
            public org.slf4j.event.Level getLevel() {
                return org.slf4j.event.Level.ERROR;
            }
        };

        new GlobalExceptionHandler().handleTicketYaException(new TicketYaException(critical409, "Se cobró y no se emitió"));

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }
}
