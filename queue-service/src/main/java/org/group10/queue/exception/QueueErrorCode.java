package org.group10.queue.exception;

import lombok.Getter;
import org.group10.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
public enum QueueErrorCode implements ErrorCode {
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    QUEUE_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND),
    EVENT_NOT_HIGH_DEMAND(HttpStatus.CONFLICT),
    SALE_NOT_STARTED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    QueueErrorCode(HttpStatus status) {
        this.status = status;
    }
}
