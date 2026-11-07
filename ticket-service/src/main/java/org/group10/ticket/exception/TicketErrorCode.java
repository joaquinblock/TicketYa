package org.group10.ticket.exception;

import lombok.Getter;
import org.group10.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
public enum TicketErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND),
    SALE_NOT_STARTED(HttpStatus.CONFLICT),
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT),
    SECTOR_SOLD_OUT(HttpStatus.CONFLICT),
    TICKET_LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT),
    TICKET_ALREADY_USED(HttpStatus.CONFLICT),
    REFUND_NOT_ALLOWED(HttpStatus.CONFLICT),
    INVALID_QR(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    TicketErrorCode(HttpStatus status) {
        this.status = status;
    }
}
