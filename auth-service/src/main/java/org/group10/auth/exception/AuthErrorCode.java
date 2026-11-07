package org.group10.auth.exception;

import lombok.Getter;
import org.group10.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
public enum AuthErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    STAFF_NOT_ASSIGNED(HttpStatus.FORBIDDEN);

    private final HttpStatus status;

    AuthErrorCode(HttpStatus status) {
        this.status = status;
    }
}
