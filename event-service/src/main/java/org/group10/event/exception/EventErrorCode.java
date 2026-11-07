package org.group10.event.exception;

import lombok.Getter;
import org.group10.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
public enum EventErrorCode implements ErrorCode {
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND),
    SECTOR_NOT_FOUND(HttpStatus.NOT_FOUND),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT),
    EVENT_NOT_EDITABLE(HttpStatus.CONFLICT);

    private final HttpStatus status;

    EventErrorCode(HttpStatus status) {
        this.status = status;
    }
}
