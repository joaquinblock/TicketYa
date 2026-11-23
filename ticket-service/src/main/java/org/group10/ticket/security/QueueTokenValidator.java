package org.group10.ticket.security;

import lombok.RequiredArgsConstructor;
import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.ticket.exception.TicketErrorCode;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class QueueTokenValidator {

    private final JwtDecoder queueTokenJwtDecoder;
    private final Clock clock;

    public void validate(String token, Long customerId, Long eventId) {
        if (token == null || token.isBlank()) {
            throw invalidQueueToken();
        }

        Jwt jwt;
        try {
            jwt = queueTokenJwtDecoder.decode(token);
        } catch (BadJwtException exception) {
            throw invalidQueueToken();
        } catch (JwtException exception) {
            throw new TicketYaException(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "No se pudo validar el turno en este momento", exception);
        }

        Instant enabledUntil = epochSecondsClaim(jwt, "enabledUntil");
        Instant expiresAt = jwt.getExpiresAt();
        Instant now = Instant.now(clock);
        if (!Long.toString(customerId).equals(jwt.getSubject())
                || !eventId.equals(integerClaim(jwt, "eventId"))
                || enabledUntil == null
                || expiresAt == null
                || enabledUntil.getEpochSecond() != expiresAt.getEpochSecond()
                || !expiresAt.isAfter(now)
                || jwt.getIssuedAt() == null
                || jwt.getId() == null
                || jwt.getId().isBlank()) {
            throw invalidQueueToken();
        }
    }

    private static Long integerClaim(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (!(claim instanceof Number number)) {
            return null;
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private static Instant epochSecondsClaim(Jwt jwt, String claimName) {
        Long seconds = integerClaim(jwt, claimName);
        if (seconds == null) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(seconds);
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private static TicketYaException invalidQueueToken() {
        return new TicketYaException(TicketErrorCode.INVALID_QUEUE_TOKEN,
                "El turno no es válido para este cliente o evento");
    }
}
