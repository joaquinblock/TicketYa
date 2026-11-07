package org.group10.payment.exception;

import lombok.Getter;
import org.group10.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT),
    PAYMENT_GATEWAY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    REFUND_FAILED(HttpStatus.BAD_GATEWAY);

    private final HttpStatus status;

    PaymentErrorCode(HttpStatus status) {
        this.status = status;
    }
}
